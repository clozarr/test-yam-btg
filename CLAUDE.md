# Project: [test-yam-btg]

## Language policy
- **All code, comments, commit messages, variable/method/class names, and Javadoc must be written in English**, regardless of the language used in the conversation with Claude.
- Documentation files (README, ADRs) also go in English unless explicitly stated otherwise.

## Tech stack
- Java 21, Spring Boot 4.x
- Spring Framework 7.x
- Spring WebFlux / Project Reactor (reactive, non-blocking)
- Gradle (Groovy)
- PostgreSQL with R2DBC (reactive driver — do NOT use blocking JPA/Hibernate in this project) for non transactional operations (Auxiliar tables, data master, logs, etc.)
- MongoDB for transactional operations (main tables, critical data)
- Redis for caching and distributed locks
- Apache Kafka (reactive producer/consumer)
- Resilience4j (circuit breaker, retry, rate limiter)
- Hexagonal architecture (ports & adapters)

## Architecture and conventions

### Package structure (hexagonal)
```
src/main/java/com/yam/funds/
├── domain/
│   ├── model/          # Domain entities, value objects
│   ├── port/
│   │   ├── in/          # Use cases (interfaces)
│   │   └── out/         # Output ports (repositories, gateways)
├── application/             # Use Cases (Orchestration of the domain)
│   └── usecase/             # Implementation of the input ports         
├── infrastructure/
│   ├── in/
│   │   ├── web/         # REST controllers (WebFlux)
│   │   └── kafka/        # Consumers
│   └── out/
│       ├── persistence/ # R2DBC repositories, mappers
│       └── kafka/        # Producers
└── config/               # Spring beans, configuration
```

### Strict rules
- **Never put business logic in adapters** (controllers, repositories). All business rules live in `domain/usecase`.
- **Reactive end-to-end**: use `Mono<T>` / `Flux<T>`. No `.block()`, manual `.subscribe()`, or any blocking operation inside a reactive chain (except in tests with `StepVerifier` or setup code).
- Preferred operators: `flatMap` for async dependent operations, `concatMap` when order matters (e.g. financial transactions), `map` only for pure synchronous transformations.
- Propagate reactive context (Reactor `Context`) for traceability (correlationId, userId) instead of `ThreadLocal`.
- Handle errors with `onErrorResume` / `onErrorMap`, never imperative try/catch around reactive chains.
- Input/output DTOs must be separate from domain models — never expose persistence entities in the web layer.

### Fintech domain — special care
- Monetary precision: always `BigDecimal`, never `double`/`float` for amounts.
- Idempotency on payment/transfer operations (idempotency key in headers or Kafka events).
- Event ordering: be careful with Kafka partitioning when transaction order matters (use the same partition key for events related to a given account/transaction).
- Transactional outbox pattern when publishing domain events alongside DB state changes.

## Kafka
- Producer: idempotent (`enable.idempotence=true`), `acks=all` on critical money flows.
- Consumer: manual ack after successful processing (no auto-commit) to avoid message loss.
- Exactly-once where applicable via the outbox pattern — don't rely solely on native Kafka transactions when there are external side-effects.

## Testing
- JUnit 5 + Reactor `StepVerifier` for reactive logic.
- **Testcontainers** for integration tests against real PostgreSQL and Kafka — do not mock the DB or the broker in integration tests.
- Mockito only for isolated unit tests of use cases.
- Expect high coverage in `domain/` — it's the core of the business.

## Commands
- Build: `./gradlew build`
- Tests: `./gradlew test`
- Integration tests: `./gradlew integrationTest` (adjust if the task has a different name)
- Start local dependencies: `docker-compose up -d`
- Run the app: `./gradlew bootRun`
- Check outdated dependencies: `./gradlew dependencyUpdates`

## Infrastructure
- Use Docker for local dependencies (PostgreSQL, Kafka, etc.).
- Use Docker Compose for local dependencies (PostgreSQL, Kafka, etc.).
- Use Kubernetes for production deployments.
- Use CloudFormation for infrastructure as code.

## Code style
- Follow existing conventions in the repo (check similar classes before creating new ones).
- Use case method names should read as business actions: `transferFunds`, not a generic `process`.
- Javadoc only on port interfaces (`port/in`, `port/out`), not on trivial implementations.
- Follow SOLID principles.
- Use Lombok annotations (`@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`) for DTOs and entities.
- Use `@Value` for immutable value objects.
- Use `@RequiredArgsConstructor` for dependency injection.
- Use `@Slf4j` for logging.
- Use `@Async` for asynchronous methods.
- Use `@Cacheable`, `@CachePut`, `@CacheEvict` for caching.
- Use `@Scheduled` for scheduled tasks.
- Use `@Profile` for environment-specific configurations.
- Use `@Configuration` for configuration classes.
- Use `@Bean` for bean definitions.
- Use `@Component` for components that are not use cases or ports.
- Use `@RestController` for REST controllers.
- Use `@Service` for service classes.
- Use `@Repository` for repositories.
- Use `@Controller` for controllers.
- Use `@RequestMapping` for request mappings.
- Use `@GetMapping` for GET requests.
- Use `@PostMapping` for POST requests.
- Use `@PutMapping` for PUT requests.
- Use `@DeleteMapping` for DELETE requests.
- Use `@PatchMapping` for PATCH requests.
- Use `@RequestBody` for request bodies.
- Use `@ResponseBody` for response bodies.
- Use `@PathVariable` for path variables.
- Use `@RequestParam` for query parameters.
- Use `@RequestHeader` for request headers.
- Use `@ResponseHeader` for response headers.
- Use `@CookieValue` for cookie values.
- Use `@MatrixVariable` for matrix variables.
- Use `@RequestPart` for multipart requests.
- Use `@RequestAttribute` for request attributes.
- Use `@SessionAttribute` for session attributes.
- Use `@RequestHeader` for request headers.
- Use `@ResponseHeader` for response headers.
- Use `@CookieValue` for cookie values.
- Use `@MatrixVariable` for matrix variables.
- Use `@RequestPart` for multipart requests.
- Use `@RequestAttribute` for request attributes.
- Use `@SessionAttribute` for session attributes.

## Logging
* **Framework:** Use SLF4J with Logback (Spring Boot's default) providing by Lombok
* **Log Levels:** Use appropriate log levels (`ERROR`, `WARN`, `INFO`, `DEBUG`, `TRACE`).
* **Contextual Logging:** Add contextual information (e.g., request ID, user ID) to logs to make tracing and debugging easier.
* ** Standard Method Logging Pattern:

When implementing method calls that require logging, follow this three-part pattern:

1. **Before Call Logging**: 
   - Log with `[methodName] [BEGIN]` prefix
   - Include relevant parameters
   - Example: `log.info("[methodName] [BEGIN] Starting operation with param {}", param)`

2. **Success Logging**:
   - Log with `[methodName] [END OK]` prefix
   - Include operation result/outcome
   - Example: `log.info("[methodName] [END OK] Operation completed with result {}", result)`

3. **Exception Logging**:
   - Log with `[methodName] [END EX]` prefix
   - Include error details and parameters
   - Example: `log.error("[methodName] [END EX] Error during operation with param {}. Details: {}", param, e.getMessage())`
   - Consider adding a secondary log with full stack trace: `log.warn(e.getLocalizedMessage(), e)`

Always wrap the operation in a try-catch block to ensure both success and error paths are properly logged.



## What NOT to do
- Do not add new dependencies without asking first.
- Do not change the existing package architecture without confirming.
- Do not use classic JPA `@Transactional` (not applicable in this reactive R2DBC stack — use `TransactionalOperator` instead).
- Do not mix blocking JDBC with R2DBC in the same flow.
