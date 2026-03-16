# CLAUDE.md

## Project Description

VIPLEV is a distributed benchmarking- and testplatform, which makes it possible to run coordinated stresstest as well as failure simulation across containerbased environments

The system consist of two main components

### VIPLEV (Central Server)

The central part of the system is running on a public available server. VIPLEV functions as a controlplanel for the entire platform, and exposes a RESTful API for which both human users and computer agents can communicate through. 

(This codebase is centered arround this part of the sytem)

### Frontend 

A frontend project will be developed so the system has a UI/UX. through this UI, the user con create environments, and define test benchmarks.

### VIPLEV Agent

A lightweight component installed in the environment to be benchmarked. The agent communicates with VIPLEV via the REST API and receives instructions on which tasks to execute. To carry out these tasks, the agent requires administrative access to the underlying containerization layer — primarily Docker and Kubernetes.

The agent's responsibilities include:
- Monitoring resource usage for individual containers/pods and the overall system (CPU, memory, network, etc.)
- Starting and stopping load testing tools (e.g. K6) based on user-defined scenarios
- Simulating container failures by controlled shutdown of selected containers/pods

The communication flow is as follows: the user defines a test scenario via the UI or REST API, VIPLEV stores and coordinates the scenario, and the agent executes it in the associated environment. Test data is continuously collected by the agent and made available to the user through VIPLEV.


## Base Package
`dk.viplev.api` — all source code lives under `src/main/java/dk/viplev/api/`

## Skills
- I have skills available in my ./.claude/skills folder.
- I have to use those when necessary.

## Architecture
The project follows **hexagonal architecture (ports & adapters)**:

- `adapter/inbound/rest/` — REST API delegates (implements generated interfaces)
- `port/inbound/` — Service interfaces (inbound ports)
- `port/outbound/db/` — Repository interfaces (outbound ports)
- `domain/model/` — JPA entities
- `domain/services/` — Service implementations
- `domain/exception/` — Custom exceptions (BadRequest, NotFound, Conflict, etc.)
- `config/security/` — JWT and Spring Security configuration

## OpenAPI-first (IMPORTANT)
API interfaces and DTOs are auto-generated from `src/main/resources/openapi/openapi.yaml` via the `openapi-generator` plugin.

- **Generated code**: `build/generated/openapi/src/main/java/` — NEVER edit these files
- **Delegate pattern**: The OpenAPI tag name determines the generated interface name: tag `Environment` → `EnvironmentApiDelegate`. We implement this in `adapter/inbound/rest/` (e.g. `EnvironmentApiDelegateImpl`)
- To add new endpoints: update `openapi.yaml`, run `./gradlew openApiGenerate`, and create a new DelegateImpl
- DTOs live in `dk.viplev.api.adapter.inbound.rest.dto` — these are GENERATED, do not create manually

## Seed Data (for testing)
Liquibase seeds 6 users (changeset 3). All have password `password`.
- Admins (ADMIN + USER roles): `admin1@viplev.dk`, `admin2@viplev.dk`
- Regular users (USER role): `user1@viplev.dk`, `user2@viplev.dk`, `user3@viplev.dk`, `user4@viplev.dk`

## Build & Test
- **Build**: `./gradlew build` (includes OpenAPI generation + tests)
- **Run app**: `./gradlew bootRun` (requires PostgreSQL + .env file)
- **Run tests**: `./gradlew test` (uses H2 in-memory DB)
- **Generate API code**: `./gradlew openApiGenerate`

## Database
- **Production**: PostgreSQL
- **Test**: H2 (PostgreSQL-compatible mode)
- **Migrations**: Liquibase — `src/main/resources/db/migrations/`
  - Changelog: `liquibase.yaml`
  - SQL files: `changes/` and `rollback/`
  - New migrations: add SQL file + changeSet in `liquibase.yaml`

## Error Handling
- RFC 7807 Problem Details format via `ErrorDTO`
- Centralized in `RestErrorHandler` (@RestControllerAdvice)
- Throw domain exceptions (`NotFoundException`, `BadRequestException`, etc.) — they are mapped automatically

## Security / JWT
- Stateless JWT-based auth (Bearer token)
- `JwtIssuer` generates tokens, `JwtDecoder` verifies
- `WebSecurityConfig` defines public vs. protected endpoints
- All endpoints are authenticated by default (`.anyRequest().authenticated()`)
- Public endpoints must be explicitly whitelisted in `WebSecurityConfig`

## GitHub Issues
- Use a MCP tool for accessing github.
- The user will only write simple tasks in github issue, I then have to use planmode to explode those issues, and evolve on the issue, together with the user, so that the explanation in the issue is as precise as possible.
- The user will tell me to implement when he's ready, no need for me asking if i should implement an issue.

## Conventions
- Lombok: `@RequiredArgsConstructor`, `@Getter`, `@Setter`, `@Slf4j`
- Java 21
- UUID as primary key
- `@CreationTimestamp` / `@UpdateTimestamp` for audit fields
