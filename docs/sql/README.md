# Part 2 — SQL

Standalone exercise: it shares nothing with the funds service beyond living in
the same repository.

> **Obtener los nombres de los clientes que tienen inscrito algún producto
> disponible solo en las sucursales que visitan.**

| File | Contents |
|---|---|
| `01-schema.sql` | The six tables from the challenge's ER diagram |
| `02-seed.sql` | Test data built around the cases that discriminate |
| `03-solution.sql` | The query |

The scripts are kept bare so they can be pasted straight into a client. The
reasoning behind them lives here.

## Reading the requirement

For a client `C` there must exist a product `P` such that:

1. `C` is enrolled in `P`, and
2. **every** branch offering `P` is a branch `C` visits.

The phrase *"disponible solo en las sucursales que visitan"* constrains **where
the product is offered**, not where the client goes. A client may visit branches
that do not offer the product — that is irrelevant. What disqualifies them is a
branch offering the product that they never visit.

This is why the challenge stresses that *"no todas las sucursales ofrecen los
mismos productos"*: the asymmetry between the two sets is the whole exercise.

SQL has no universal quantifier, so condition 2 becomes a double negation:

> there is no branch offering `P` that the client does not visit

which is exactly what the nested `NOT EXISTS` in `03-solution.sql` says.

## Interpretation declared

A product offered in **no branch at all** satisfies condition 2 vacuously — the
set of branches to check is empty, so nothing can violate it. That is almost
certainly not the intent, so the query requires the product to be offered
somewhere, with the leading `EXISTS`. Removing it includes those clients.

In the seed data this is Felipe, enrolled only in a product no branch offers.

## Running it

PostgreSQL, using the container from `docker-compose.yml`:

```bash
docker exec yam-postgres psql -U funds -d postgres -c "CREATE DATABASE yam;"
docker exec -i yam-postgres psql -U funds -d yam < docs/sql/01-schema.sql
docker exec -i yam-postgres psql -U funds -d yam < docs/sql/02-seed.sql
docker exec -i yam-postgres psql -U funds -d yam < docs/sql/03-solution.sql
```

Column names are kept exactly as the challenge specifies (`idProducto`,
`fechaVisita`, …) and are double-quoted throughout. PostgreSQL folds unquoted
identifiers to lower case, so without the quotes `idProducto` and `idproducto`
would silently be the same column — and querying by hand needs the quotes too:

```sql
SELECT * FROM inscripcion WHERE "idCliente" = 1;   -- works
SELECT * FROM inscripcion WHERE idCliente = 1;     -- column "idcliente" does not exist
```

## The test data, and why it looks like that

Random data would let an incorrect query return the same rows as a correct one,
which proves nothing. Every client exists to exercise one specific edge:

| Client | Product offered in | Visits | Qualifies |
|---|---|---|---|
| Ana | {1, 2} | {1, 2, 3} | **yes** — offering branches are a subset |
| Bruno | {1, 2, 3} | {1, 2} | no — misses branch 3 |
| Carla | {4} | {4, 5} | **yes** — single offering branch, visited |
| Diego | {1, 2, 3, 4, 5} | {1, 2, 3, 4} | no — visits four, misses the fifth |
| Elena | {1,2,3,4,5} and {1, 2} | {1, 2, 3, 4} | **yes** — via the second product only |
| Felipe | ∅ | {1} | no — offered nowhere (vacuous truth) |
| Gloria | {1, 2, 3} | ∅ | no — visits nothing |
| Hugo | {1, 2, 3} | {1, 2, 3} | **yes** — exact match |

**Expected answer: Ana, Carla, Elena, Hugo.**

Bruno and Diego are the load-bearing rows. Both visit *some* of the branches
offering their product, so any query that tests "offered in a branch the client
visits" returns them — and is wrong.

Elena covers the word *algún*: she holds two products and qualifies through only
one of them. Hugo covers exact equality between the two sets, Carla the
single-branch case, Gloria the empty visit set.

## The answer most people write first

This is the query that comes out if the requirement is read as a membership test
rather than a subset test:

```sql
SELECT DISTINCT c.nombre
FROM cliente c
JOIN inscripcion i    ON i."idCliente"  = c.id
JOIN disponibilidad d ON d."idProducto" = i."idProducto"
JOIN visitan v        ON v."idSucursal" = d."idSucursal"
                     AND v."idCliente"  = c.id
ORDER BY c.nombre;
```

It asks *"is the product offered in **some** branch the client visits?"*, a far
weaker condition. Against the same seed it returns six names instead of four:

```
correct →  Ana, Carla, Elena, Hugo
naive   →  Ana, Bruno, Carla, Diego, Elena, Hugo
```

Bruno and Diego are the difference. Running both is what makes the distinction
demonstrable rather than asserted.

## Two equivalent formulations

Which to prefer is a readability argument, not a correctness one — all three
return the same four names.

**As a set difference.** "The branches offering the product, minus the branches
the client visits, must be empty." Often the easiest to say out loud:

```sql
SELECT DISTINCT c.nombre
FROM cliente c
JOIN inscripcion i ON i."idCliente" = c.id
WHERE EXISTS (
        SELECT 1 FROM disponibilidad d WHERE d."idProducto" = i."idProducto"
      )
  AND NOT EXISTS (
        SELECT d."idSucursal"
        FROM disponibilidad d
        WHERE d."idProducto" = i."idProducto"
        EXCEPT
        SELECT v."idSucursal"
        FROM visitan v
        WHERE v."idCliente" = c.id
      )
ORDER BY c.nombre;
```

**As an aggregate.** Count the offering branches, count how many of those the
client visits, require the two to match. Avoids nested negation, but reads
furthest from the sentence it implements:

```sql
SELECT DISTINCT c.nombre
FROM cliente c
JOIN inscripcion i    ON i."idCliente"  = c.id
JOIN disponibilidad d ON d."idProducto" = i."idProducto"
LEFT JOIN visitan v   ON v."idSucursal" = d."idSucursal"
                     AND v."idCliente"  = c.id
GROUP BY c.id, c.nombre, i."idProducto"
HAVING COUNT(*) = COUNT(v."idCliente")
ORDER BY c.nombre;
```

The `LEFT JOIN` is what makes this work: `COUNT(*)` counts every offering branch,
while `COUNT(v."idCliente")` skips the NULLs left by branches the client never
visited. Equal counts mean there were no such branches.

The nested `NOT EXISTS` in `03-solution.sql` is the one to keep: it is closest to
the sentence being implemented, and a planner executes it as an anti-join.

## Verified output

All three formulations, run against PostgreSQL 18 with the seed above:

```
 nombre
--------
 Ana
 Carla
 Elena
 Hugo
```
