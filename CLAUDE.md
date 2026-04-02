# CLAUDE.md

## Project Description
VIPLEV is a distributed benchmarking platform. This codebase is the central REST API server (Spring Boot, Java 21).

## Skills
- Skills are available in `.claude/skills` — use them when relevant.

## OpenAPI-first (IMPORTANT)
API interfaces and DTOs are auto-generated from `src/main/resources/openapi/openapi.yaml`.

- Generated code in `build/generated/openapi/` — NEVER edit
- Delegate pattern: OpenAPI tag name → generated interface (e.g. tag `Environment` → `EnvironmentApiDelegate`), implemented in `adapter/inbound/rest/`
- New endpoints: update `openapi.yaml`, run `./gradlew openApiGenerate`, create DelegateImpl
- DTOs in `dk.viplev.api.adapter.inbound.rest.dto` are GENERATED — do not create manually

## Seed Data
All seed users have password `password`.
- Admins: `admin1@viplev.dk`, `admin2@viplev.dk`
- Users: `user1@viplev.dk` – `user4@viplev.dk`

## Build & Test
- **Build**: `./gradlew build`
- **Run**: `./gradlew bootRun` (requires PostgreSQL + .env)
- **Test**: `./gradlew test` (H2 in-memory)
- **Generate API**: `./gradlew openApiGenerate`

## Database
- Production: PostgreSQL / Test: H2 (PostgreSQL-compatible mode)
- Migrations: Liquibase — `src/main/resources/db/migrations/`
- No rollback files
