# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Overview

This is a concept-proof repository for the `ms-file` microservice in the Cirrus/ProMujer platform. The `.github/skills/` folder contains the authoritative reference documentation for building microservices in this platform. The `ms-file-s3-acl-demo/` subdirectory contains a SeaweedFS S3 ACL demonstration.

## Build Commands

```bash
# Clean and compile
./mvnw clean compile

# Run tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=SomeBlTest

# Package JAR (skip tests)
./mvnw clean package -DskipTests

# Run locally
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## Tech Stack

- **Java 21** — use switch expressions, text blocks, and pattern matching
- **Spring Boot 4.0.2** — webmvc (not webflux), data-jpa, security, oauth2-resource-server, validation
- **SQL Server** — primary database; DDL scripts are provided by the Database Team, never auto-generated
- **Keycloak** — OAuth2/OIDC identity provider (JWT resource server)
- **`commons-backend` 1.0.6** — internal library from Google Artifact Registry (`us-central1-maven.pkg.dev/cirrus-repo/cirrus-maven`)

## Package Structure

```
bo.cirrus.pocketbank.ms_{service}/
├── Ms{Service}Application.java
├── api/           # REST controllers — named {Entity}Api.java
├── bl/            # Business logic — named {Entity}Bl.java
│   └── {feature}/ # Strategy implementations
├── config/        # Spring configurations
├── dao/
│   ├── domain/    # JPA entities
│   └── repository/ # Spring Data repositories
├── dto/           # Request/response DTOs, organized by domain
├── enums/
├── services/      # External HTTP clients (RestTemplate/Feign)
├── util/
└── validation/    # {Entity}Validation.java components
```

## Key Patterns

### Controllers (`api/`)
Every endpoint follows this exact sequence:
1. `LOGGER.info("API: methodName - ...")` with key params
2. `RequestParamDto requestParamDto = new RequestParamDto(request, tracer)`
3. Call BL method with `token` and `customerId` extracted from `requestParamDto`
4. Return `ResponseEntity.ok(new ResponseDto<>(result))`

### Commons Library
- `RequestParamDto(request, tracer)` — extracts `token`, `customerId`, `channel`, `ruleMode` from HTTP headers
- `ResponseDto<T>` — wraps all responses: `{ "data": ..., "success": true, "message": null }`
- `ServiceException(code, message)` — for business rule violations; use structured error codes by domain (e.g., 1000–1099 for one domain)
- `Execute.orFail(supplier, errorLogger)` — wraps all external microservice calls with automatic error handling

### Strategy Pattern (BL layer)
When an operation has multiple implementations by type:
- Define `interface Feature { FeatureType type(); ResultDto execute(...); }`
- Each implementation is `@Component` and returns its unique `type()`
- Orchestrator `FeatureBl` receives `List<Feature>` in constructor and converts to `Map<FeatureType, Feature>` via `Collectors.toMap(Feature::type, Function.identity())`

### Dependency Injection
Always use constructor injection with `final` fields — no `@Autowired` on fields.

### Logging Prefixes
| Prefix | Layer |
|--------|-------|
| `API:` | Controller |
| `BL:` | Business logic |
| `BL-{STRATEGY}:` | Strategy (e.g., `BL-OWN:`) |
| `SERVICE:` / `SUCCESS-SERVICE:` / `ERROR-SERVICE:` | External calls |
| `VALIDATION:` | Validation components |
| `BL-PERSISTENCE:` | Persistence BL |
| `ERROR-BL:` | BL errors |

Never log tokens or PII.

### Transactions
Persistence operations that must commit independently (audit trail, status tracking) use a dedicated `{Entity}PersistenceBl` with `@Transactional(propagation = Propagation.REQUIRES_NEW)`.

## Database Conventions

- **Developers do not write SQL scripts** — schema changes are requested from and provided by the Database Team
- Table prefixes: `tf_` (transfer), `be_` (beneficiary), `pb_` (shared/core), `h_` (history/audit), `ob_` (onboarding), `ct_` (catalog/lookup)
- Every table requires: `{entity}_id INT` (PK via sequence), `version INT` (optimistic locking), `created_at DATETIME`, `updated_at DATETIME`
- Sequences named `sq_{table}_id`; accessed via `@Query(value = "SELECT NEXT VALUE FOR sq_{table}_id", nativeQuery = true)`
- Transactional tables need a paired history table `h_{table}` with INSERT/UPDATE triggers
- JPA entities use `@Version` on the `version` column and explicit `@Column(name = "...")` mappings

## Unit Testing

Tests are pure unit tests — no Spring context.

```java
@ExtendWith(MockitoExtension.class)
class SomeBlTest {
    @Mock private SomeRepository repo;
    private SomeBl bl;

    @BeforeEach
    void setUp() {
        // Validation is instantiated directly (NOT mocked)
        SomeValidation validation = new SomeValidation();
        bl = new SomeBl(repo, validation);
    }
}
```

- Mock all dependencies **except** `{Entity}Validation` classes — instantiate those as real objects
- Use `assertThrows(ServiceException.class, () -> ...)` and assert `ex.getCode()` against public constants on the Validation class
- Import: `org.assertj.core.api.Assertions.assertThat`, `org.mockito.ArgumentMatchers.*`, `org.mockito.Mockito.*`

## Endpoint Testing (Bruno CLI)

API collections live in `http/` at the project root.

```bash
npm install -g @usebruno/cli
bruno run http/ --env dev
bruno run http/transfer/request-transfer.bru --env dev
```

## S3 ACL Demo (`ms-file-s3-acl-demo/`)

Demonstrates bucket-level ACL with SeaweedFS (S3-compatible). Key design decisions:
- One `S3Client` bean per identity (client-uploader, processor-service, reader-service, admin)
- `forcePathStyle(true)` required — SeaweedFS doesn't support virtual-hosted style URLs
- `S3Presigner` is separate from `S3Client` for generating presigned PUT URLs
- Docker Compose uses `depends_on: service_completed_successfully` so the app waits for bucket init

Start the demo:
```bash
cd ms-file-s3-acl-demo
docker compose up
```

## Configuration

All config values use environment variable substitution with defaults:
```yaml
spring.datasource.url: jdbc:sqlserver://${DB_HOST:localhost}:${DB_PORT:1433};database=${DB_NAME:pocketbank}
spring.jpa.hibernate.ddl-auto: none   # always none — DB Team owns schema
```

Key runtime env vars: `SPRING_PROFILE`, `DB_HOST`, `DB_PASSWORD`, `AUTH_SERVER_URL`, `AUTH_REALM`, external service `*_SERVICE_URL` vars.
