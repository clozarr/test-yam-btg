# YAM Funds

Clients subscribe to investment funds, cancel subscriptions and read their
transaction history, without going through an adviser.

Solution to the back-end technical challenge (see [`CHALLENGE.md`](CHALLENGE.md)).
Part 1 is this service; Part 2, the SQL exercise, lives in
[`docs/sql/`](docs/sql/).

---

## Quick start

Requires Docker and JDK 21. Nothing else — Gradle comes with the wrapper.

```bash
docker compose up -d                                    # MongoDB, PostgreSQL, Kafka
./gradlew bootRun --args='--spring.profiles.active=local'
```

Wait for all three containers to report `healthy` (`docker compose ps`), then:

```bash
curl localhost:8080/api/v1/funds
```

Five funds means the whole chain works: HTTP, use case, PostgreSQL and its seed.

> The `local` profile matters. It is what exposes `POST /dev/token`, which stands
> in for an identity provider. Without it there is no way to obtain a bearer
> token and every other endpoint answers 401.

**MongoDB is published on 27018**, not the default, so a natively installed
`mongod` can keep 27017.

### Getting a token

```bash
# Admin — required to register clients
curl -X POST 'localhost:8080/dev/token?clientId=admin&roles=ADMIN'

# A specific client
curl -X POST 'localhost:8080/dev/token?clientId=<CLIENT_ID>&roles=CLIENT'
```

Use the returned `accessToken` as `Authorization: Bearer <token>`.

### Exercising the API

[`docs/postman/yam-funds.postman_collection.json`](docs/postman/) covers every
scenario in the challenge — 25 requests, 64 assertions. Import it and run the
folders **in order** with the Collection Runner: requests share state, and the
balance assertions build on one another.

Interactive docs at `http://localhost:8080/swagger-ui.html`.

---

## The API

| Method | Path | Who |
|---|---|---|
| `POST` | `/api/v1/clients` | admin only |
| `GET` | `/api/v1/clients/{clientId}` | the client, or admin |
| `GET` | `/api/v1/funds` | public |
| `POST` | `/api/v1/clients/{clientId}/subscriptions/{fundId}` | the client, or admin |
| `DELETE` | `/api/v1/clients/{clientId}/subscriptions/{fundId}` | the client, or admin |
| `GET` | `/api/v1/clients/{clientId}/transactions` | the client, or admin |
| `POST` | `/dev/token` | `local` profile only |

Both operations that move money require an **`Idempotency-Key` header**.

### Business rules

- Every client is registered with an opening balance of **COP $500,000**. It comes
  from configuration, never from the request — letting a caller choose it would be
  a way to mint money.
- Every transaction carries a unique identifier.
- Each fund has a minimum subscription amount.
- Cancelling returns the linked amount **in full**: no yield, no penalty.
- With insufficient balance the response is `422` and reads exactly
  *"No tiene saldo disponible para vincularse al fondo &lt;Nombre del fondo&gt;"*.
- Subscribing notifies the client over their preferred channel, email or SMS.

### Errors

| Status | When |
|---|---|
| `400` | Malformed request, missing `Idempotency-Key`, invalid cursor |
| `401` | No token, or an invalid one |
| `403` | Reading or spending another client's account |
| `404` | Unknown client, fund or subscription |
| `409` | Already subscribed, or a request with the same key still running |
| `422` | Insufficient balance, below the minimum, idempotency key reused |

`422` rather than `400` for insufficient balance: the request is perfectly valid,
the account simply cannot cover it.

Every error body carries a `correlationId`. A `500` returns that identifier
instead of the cause, which can leak connection strings or internal detail.

---

## Architecture

Hexagonal, with the dependency rule enforced by package structure: `domain`
imports nothing from Spring — only the JDK and Reactor.

```
com.yam.funds
├── domain/            model, ports in/out, exceptions   ← no framework imports
├── application/       use cases orchestrating the domain
├── infrastructure/
│   ├── in/web         controllers, DTOs, error handling, security
│   ├── in/kafka       notification consumer
│   ├── out/persistence MongoDB (transactional) · PostgreSQL (master data)
│   ├── out/kafka      outbox relay and producer
│   └── out/notification email and SMS senders
└── config/            Spring wiring
```

Reactive end to end (`Mono`/`Flux`, no blocking calls), Java 21 records in the
domain, Lombok where the persistence frameworks need mutability.

### Why two databases

| Store | Holds | Why |
|---|---|---|
| **MongoDB** | clients, ledger, outbox, idempotency | Transactional data. Needs multi-document transactions and a unique index |
| **PostgreSQL** | fund catalogue | Master data: read often, written almost never, takes no part in moving money |

MongoDB runs as a **replica set**, even locally with a single node —
multi-document transactions are rejected on a standalone server.

---

## The NoSQL data model

Four collections. The shape of the first one is the central design decision.

### `clients` — the aggregate

```json
{
  "_id": "1f941bf9-51a6-4642-aa78-5f013b74edf8",
  "fullName": "Ada Lovelace",
  "email": "ada@example.com",
  "phone": "+573001112233",
  "notificationPreference": "EMAIL",
  "balance": { "amount": "425000.00", "currency": "COP" },
  "activeSubscriptions": {
    "1": {
      "id": "3be88a4f-2cf3-4908-890b-c5cfaa13b477",
      "fundId": "1",
      "fundName": "FPV_AM_PACTUAL_RECAUDADORA",
      "linkedAmount": { "amount": "75000.00", "currency": "COP" },
      "status": "ACTIVE",
      "openedAt": "2026-08-13T15:35:22.041Z"
    }
  },
  "version": 3
}
```

Spring Data adds a `_class` type hint to every document; it is omitted above for
readability.

**Balance and subscriptions live in one document** because the rule "cannot
subscribe without sufficient balance" couples them: they change together or not
at all. As a single document, that invariant is protected by document-level
atomicity, with no transaction required.

**Only active subscriptions are embedded, keyed by fund.** That bounds the
document by the size of the catalogue — five entries at most — and makes "one
active subscription per fund" a structural guarantee rather than a check.
Cancelled ones are not lost: they survive in the ledger.

`version` drives optimistic locking, so two concurrent subscriptions cannot both
succeed against a stale balance.

**Amounts are stored as strings.** Spring Data's default representation for
`BigDecimal` has changed between versions, and a silent switch to binary floating
point would corrupt balances.

### `fund_transactions` — the ledger

Append-only; entries are never updated or deleted. Correcting the ledger means
appending a compensating entry, not editing history.

```json
{
  "_id": "2a176ee3-dc16-4c29-a510-f125c4acafe7",
  "clientId": "1f941bf9-...", "fundId": "1",
  "fundName": "FPV_AM_PACTUAL_RECAUDADORA",
  "subscriptionId": "3be88a4f-...",
  "type": "OPENING",
  "amount":       { "amount": "75000.00",  "currency": "COP" },
  "balanceAfter": { "amount": "425000.00", "currency": "COP" },
  "occurredAt": "2026-08-13T15:35:22.041Z"
}
```

Indexed on `{clientId: 1, occurredAt: -1, _id: -1}`, matching how history is
paged. Paging is **cursor-based**, not offset-based: the ledger grows at the head,
so an offset would shift under a reader as new entries arrive.

### `idempotency_records`

The scoped key is the document `_id`, so MongoDB's primary key is what rejects a
duplicate. A TTL index on `expiresAt` reaps completed records without a cleanup
job.

```json
{
  "_id": "1f941bf9-...:SUBSCRIBE:key-aaaa-0001",
  "status": "COMPLETED",
  "requestFingerprint": "9f2c…",
  "responsePayload": "2a176ee3-dc16-4c29-a510-f125c4acafe7",
  "leaseExpiresAt": "…", "expiresAt": "…"
}
```

### `outbox_events`

Events awaiting publication, written inside the same transaction as the state
change they describe.

---

## Idempotency

The part worth reading the code for. Both money-moving operations require an
`Idempotency-Key`; repeating a request with the same key returns the original
result rather than charging twice.

The sequence is **reserve, then execute**. The reservation is inserted before any
work starts, so a **unique-key violation — not a read-then-write check — is what
serialises two duplicates arriving at the same instant**. A check-then-act
approach has a window between the two, and concurrent retries land exactly in it.

The reservation is promoted to completed **inside the same transaction** as the
operation, closing the window where money could have moved while the key still
read as in progress. A rejected request commits nothing and releases its key, so
the caller can correct the request and retry immediately.

A repeated key resolves to one of four outcomes:

| Situation | Result |
|---|---|
| Same key, different body | `422` — a caller bug, not something to replay |
| Completed, same body | replay of the original transaction |
| In progress, lease valid | `409` — a twin request is running |
| In progress, lease expired | taken over from a crashed instance |

Replays are rebuilt from the ledger by transaction id rather than from a stored
response, so the reply cannot drift out of step with what was recorded.

## Notifications

Subscribing records an event in the **outbox** inside the same transaction as the
balance change, and a relay forwards it to Kafka afterwards. Publishing directly
would either announce subscriptions that later rolled back, or fail a
subscription because a broker was unreachable.

Events are keyed by client id, since Kafka only orders records within a
partition. Delivery is at-least-once by deliberate choice: marking an event
published before sending would lose it instead, which is worse for a notice about
someone's money.

The senders are simulated — they log instead of contacting a provider, masking
addresses and phone numbers. Swapping in SES or Twilio means replacing one class
behind the port.

```
[handle] [END OK] partition=0 offset=1
[send] [EMAIL] to=a***@example.com subject='Subscription confirmed' ...
```

---

## Security

Stateless JWT resource server. Ownership is checked per endpoint by comparing the
token subject against the `clientId` in the path — without it, any authenticated
client could read or spend another's balance by editing the URL.

Client registration is admin-only: a client cannot present a token for an account
that does not exist yet.

Tokens are HMAC-signed and minted by `POST /dev/token`, restricted to the `local`
profile. An unauthenticated endpoint issuing tokens for any subject would be a
complete authentication bypass, so it cannot exist in a deployed environment.
Pointing at a real issuer means replacing the decoder bean; nothing else changes.

---

## Testing

```bash
./gradlew test      # 110 unit tests
./gradlew build
```

Unit tests cover the domain rules, every branch of the idempotency logic, the
persistence mappers and the HTTP contract. Mockito and `StepVerifier`; no Spring
context, so they run in seconds.

Beyond that, the API has been exercised end to end against MongoDB, PostgreSQL
and Kafka in containers — subscription, idempotent replay, retry after a
rejection, cancellation, history paging, the IDOR checks and notification
delivery. The Postman collection replays all of it.

---

## Configuration

Everything has a working default for local development. Override with
environment variables:

| Variable | Default |
|---|---|
| `MONGODB_URI` | `mongodb://localhost:27018/funds?directConnection=true` |
| `DB_URL` | `r2dbc:postgresql://localhost:5432/funds` |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `JWT_SECRET` | a development value — **must be overridden outside local** |
| `SERVER_PORT` | `8080` |

`directConnection=true` is required, not cosmetic: the containerised replica set
advertises its member as `localhost:27017`, so a driver doing topology discovery
would redirect itself there — a different MongoDB entirely.

---

## Part 2 — SQL

The query, its schema and its seed data are in [`docs/sql/`](docs/sql/).

> *Obtener los nombres de los clientes que tienen inscrito algún producto
> disponible solo en las sucursales que visitan.*

It is a **subset test**, not a membership test: the phrase constrains where the
product is offered, not where the client goes. A client may visit branches that
do not offer it; what disqualifies them is a branch that offers it and they never
visit. SQL has no universal quantifier, so it is written as a double negation —
there is no offering branch the client fails to visit.

```bash
docker exec yam-postgres psql -U funds -d postgres -c "CREATE DATABASE yam;"
docker exec -i yam-postgres psql -U funds -d yam < docs/sql/01-schema.sql
docker exec -i yam-postgres psql -U funds -d yam < docs/sql/02-seed.sql
docker exec -i yam-postgres psql -U funds -d yam < docs/sql/03-solution.sql
```

The seed is designed rather than random. Bruno visits two of the three branches
offering his product and Diego four of five, so a query asking "offered in *some*
branch the client visits" returns them and is wrong. Expected answer: **Ana,
Carla, Elena, Hugo**.

Column names are kept exactly as the challenge specifies and double-quoted
throughout, since PostgreSQL folds unquoted identifiers to lower case and would
otherwise treat `idProducto` and `idproducto` as the same column.

## Not done yet

**AWS CloudFormation deployment.** The challenge asks for it and it is not here.
The architecture is decided — ECS Fargate behind an ALB, RDS for the catalogue,
managed MongoDB and Kafka — but a template that nobody runs is worth less than
being honest about what a reviewer would actually find, and a naive one invites
an expensive accidental deployment.
