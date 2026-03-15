# viplev


## Run with Gradle

The project uses the [Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html), which is included in the repository. No separate Gradle installation is required.

### Prerequisites

- Java 21 (managed via Gradle [toolchain](https://docs.gradle.org/current/userguide/toolchains.html))
- A `.env` file in the project root (used by the `dotenv-gradle` plugin for `bootRun`)

### Common commands

```bash
# Start the application locally (loads .env automatically)
./gradlew bootRun

# Run all tests
./gradlew test

# Compile without running tests
./gradlew classes

# Full build (compile + test + jar)
./gradlew build

# Build only the boot jar (output: build/libs/viplev-api.jar)
./gradlew bootJar
```

### OpenAPI code generation

API interfaces and DTOs are generated automatically from `src/main/resources/openapi/openapi.yaml` as part of `compileJava`. You can also run it manually:

```bash
# Generate API interfaces and DTOs
./gradlew openApiGenerate
```

Generated code is placed in `build/generated/openapi/` and included in the source set automatically.

### Cleanup and troubleshooting

```bash
# Delete build output (including generated code)
./gradlew clean

# Clean build from scratch
./gradlew clean build

# List all available tasks
./gradlew tasks

# Show dependency tree (useful for finding version conflicts)
./gradlew dependencies

# Run build with debug output
./gradlew build --info
```

### Docker

```bash
# Build Docker image
docker build -t viplev-api .

# Run container
docker run -p 8080:8080 viplev-api
```

## Spring Security

The application uses stateless JWT-based authentication. There are no sessions — every request is authenticated independently via a token in the `Authorization` header. The security configuration lives in `WebSecurityConfig`, which disables CSRF, sessions, and form login, and registers a custom JWT filter.

All endpoints require authentication **except**:
- `/` — root
- `/v1/auth/login` — login endpoint
- `/swagger-ui/**` and `/v3/api-docs/**` — API documentation

The `/admin` endpoint additionally requires the `ADMIN` role.

### JWT Issuing (Login)

When a client sends a `POST /v1/auth/login` with email and password, the request flows through:

```
Client
  │
  ▼
AuthApiDelegateImpl                  (REST layer — receives LoginDTO)
  │
  ▼
AuthServiceImpl                      (Service layer — orchestrates login)
  │
  ├──▶ AuthenticationManager.authenticate()
  │       │
  │       ▼
  │     CustomUserDetailsService     (Loads user from DB via UserRepository)
  │       │
  │       ▼
  │     UserPrincipal                (UserDetails impl — wraps id, email, password, roles)
  │       │
  │       ▼
  │     BCryptPasswordEncoder        (Verifies password against stored hash)
  │
  ├──▶ JwtIssuer.issue()             (Creates signed JWT with userId, email, roles)
  │       │
  │       ▼
  │     JwtProperties                (Provides HMAC256 secret key)
  │
  ▼
Client receives LoginDTO with email + token
```

**Step by step:**

1. `AuthApiDelegateImpl` receives the request and delegates to `AuthService.attemptLogin(email, password)`.
2. `AuthServiceImpl` creates a `UsernamePasswordAuthenticationToken` and passes it to Spring's `AuthenticationManager`.
3. The `AuthenticationManager` (configured in `WebSecurityConfig`) calls `CustomUserDetailsService.loadUserByUsername(email)`, which queries the database via `UserRepository.findByEmail()` and returns a `UserPrincipal`.
4. The `AuthenticationManager` compares the provided password against the stored BCrypt hash using `BCryptPasswordEncoder`.
5. If authentication succeeds, `AuthServiceImpl` extracts the `UserPrincipal` from the authentication result.
6. It strips the `ROLE_` prefix from authorities and calls `JwtIssuer.issue(userId, email, roles)`.
7. `JwtIssuer` creates a JWT signed with HMAC256 (secret from `JwtProperties`), containing the `sub` (userId), `email`, `roles`, and an expiration time.
8. The token is returned to the client in a `LoginDTO`.

### JWT Verification (Usage)

For every subsequent request, the JWT is verified by a filter that runs before Spring Security's built-in `UsernamePasswordAuthenticationFilter`:

```
Client (Authorization: Bearer <token>)
  │
  ▼
JwtAuthenticationFilter              (Extracts token from Authorization header)
  │
  ├──▶ JwtDecoder.decode()           (Verifies signature + expiration via HMAC256)
  │       │
  │       ▼
  │     JwtProperties                (Provides the same secret key used for signing)
  │
  ├──▶ JwtToPrincipalConverter       (Maps decoded JWT claims → UserPrincipal)
  │
  ├──▶ UserPrincipalAuthenticationToken  (Wraps UserPrincipal as authenticated token)
  │
  ├──▶ SecurityContextHolder         (Stores authentication for the request)
  │
  ▼
Request continues to controller (authenticated)
```

**Step by step:**

1. `JwtAuthenticationFilter` (extends `OncePerRequestFilter`) intercepts every request.
2. It checks the `Authorization` header for a `Bearer <token>` value.
3. If no token is present, the filter chain continues unauthenticated — Spring Security will reject the request if the endpoint requires authentication (via `CustomAuthenticationEntryPoint`, which returns a 401 JSON response).
4. If a token is present, `JwtDecoder.decode()` verifies the HMAC256 signature and checks expiration using the secret from `JwtProperties`. If verification fails, the `SecurityContext` is cleared and the request continues unauthenticated.
5. On successful verification, `JwtToPrincipalConverter.convert()` extracts `sub` (userId), `email`, and `roles` from the JWT claims and builds a `UserPrincipal`. Roles are mapped to Spring authorities with the `ROLE_` prefix (e.g., `ADMIN` becomes `ROLE_ADMIN`).
6. The `UserPrincipal` is wrapped in a `UserPrincipalAuthenticationToken` (extends `AbstractAuthenticationToken`), which is marked as authenticated.
7. The token is placed in `SecurityContextHolder`, making it available for the rest of the request.
8. Spring Security's authorization rules (from `WebSecurityConfig`) then check whether the authenticated user has access to the requested endpoint.

### Error Handling

Two custom handlers produce JSON error responses instead of Spring's default HTML:

- **`CustomAuthenticationEntryPoint`** — returns `401 Unauthorized` when a request reaches a secured endpoint without valid authentication.
- **`CustomAccessDeniedHandler`** — returns `403 Forbidden` when an authenticated user lacks the required role (e.g., a non-admin hitting `/admin`).

Both return an `ErrorDTO` with `status`, `title`, `detail`, and a link to the relevant MDN HTTP status documentation.