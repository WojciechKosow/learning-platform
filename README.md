# Learning Platform — backend

Spring Boot backend for an AI-powered platform that teaches programming through
real projects. This repository currently contains the **foundation**: accounts,
authentication, security, persistence and error handling. Learning features
(assessment, skill profile, projects, AI mentoring, code execution, GitHub) are
built on top of it — see [docs/architecture.md](docs/architecture.md).

**Stack:** Java 21 · Spring Boot 4.1 · Spring Security 7 (OAuth2 resource server, HS256 JWT) ·
Spring Data JPA / Hibernate 7 · PostgreSQL · Flyway · Testcontainers.

---

## Quick start (local development)

Prerequisites: **JDK 21** and **Docker** (for PostgreSQL and the tests). Maven
is not needed — use the wrapper (`./mvnw`, or `mvnw.cmd` on Windows).

```bash
# 1. Start PostgreSQL (localhost:5432, db/user/password: learning_platform)
docker compose up -d

# 2. Run the API on http://localhost:8080 with the `dev` profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The `dev` profile (`src/main/resources/application-dev.yml`) supplies
local-only defaults: the compose database password, a **public development JWT
key**, `CORS_ALLOWED_ORIGINS=http://localhost:3000` and non-`Secure` cookies (plain
http). It also loads an optional `.env` file from the working directory — copy
`.env.example` to `.env` to override anything. **Never enable `dev` in a deployed
environment.**

Flyway creates the schema on startup. To reset the local database:
`docker compose down -v && docker compose up -d`.

Alternative without compose — a throwaway PostgreSQL container that is
discarded when the app stops:

```bash
./mvnw spring-boot:test-run
```

Smoke test:

```bash
curl -i -c cookies.txt -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"ada@example.com","username":"ada_lovelace","password":"correct-horse-battery"}'
```

## Running tests

```bash
./mvnw test        # needs a running Docker daemon
./mvnw verify      # tests + packaging
```

Integration tests start their own PostgreSQL via Testcontainers
(`postgres:17-alpine`) and never touch your local or a shared database.

## Configuration

All settings live in `src/main/resources/application.yml` as
`${ENV_VAR:default}` placeholders. Nothing secret is committed. The app **refuses
to start** without `JWT_SECRET` (≥ 32 bytes) and cannot connect without
`DB_PASSWORD` (outside the `dev` profile).

| Variable | Required in prod | Default | Purpose |
|---|---|---|---|
| `DB_URL` | yes | `jdbc:postgresql://localhost:5432/learning_platform` | JDBC URL |
| `DB_USERNAME` | yes | `learning_platform` | DB user |
| `DB_PASSWORD` | **yes** | *(none)* | DB password |
| `JWT_SECRET` | **yes** | *(none)* | HS256 signing key, ≥ 32 random bytes: `openssl rand -base64 48` |
| `JWT_ISSUER` | no | `learning-platform` | `iss` claim (set and verified) |
| `JWT_ACCESS_TOKEN_TTL` | no | `15m` | Access-token lifetime |
| `CORS_ALLOWED_ORIGINS` | if the frontend is on another origin | *(empty = none)* | Comma-separated exact origins, e.g. `https://app.example.com` |
| `AUTH_REFRESH_TOKEN_TTL` | no | `1d` | Session length without "remember me" |
| `AUTH_REMEMBER_ME_REFRESH_TOKEN_TTL` | no | `30d` | Session length with "remember me" |
| `AUTH_MAX_FAILED_LOGINS` | no | `5` | Failed passwords before login is locked |
| `AUTH_LOGIN_LOCK_DURATION` | no | `15m` | Lock duration |
| `AUTH_COOKIE_SECURE` | no | `true` | `Secure` flag on the refresh cookie |
| `AUTH_COOKIE_SAME_SITE` | no | `Lax` | `Strict`, `Lax` or `None` (`None` requires `Secure`) |
| `AUTH_COOKIE_PATH` | no | `/api/auth` | Must match the browser-visible path of the auth endpoints |
| `AUTH_COOKIE_DOMAIN` | no | *(host-only)* | Cookie domain, rarely needed |
| `PORT` | no | `8080` | HTTP port |

Durations accept `15m`, `1d`, `PT15M`, etc. A gitignored
`src/main/resources/application.properties` can still be used for personal
overrides.

## Database and migrations

- The schema is owned by **Flyway** (`src/main/resources/db/migration`).
  Hibernate runs with `ddl-auto: validate` and never changes the schema.
- Add a change as a new file `V<n>__<description>.sql`; never edit a migration
  that has been applied anywhere.
- `flyway clean` is disabled. Nothing in the application seeds, deletes or
  rewrites user data automatically.

Current tables: `users`, `refresh_tokens` (see `V1__create_users_and_refresh_tokens.sql`).

## Authentication design

| | Access token | Refresh token |
|---|---|---|
| Format | JWT, HS256, claims `sub` (user id), `iss`, `iat`, `exp`, `jti` | 256-bit random value |
| Lifetime | 15 min (`JWT_ACCESS_TOKEN_TTL`) | 1 day, or 30 days with "remember me" (absolute, not extended by refreshing) |
| Delivered as | JSON body (`accessToken`) | `HttpOnly` cookie `lp_refresh_token`, `Path=/api/auth`, `SameSite=Lax`, `Secure` |
| Sent as | `Authorization: Bearer <token>` | Automatically by the browser to `/api/auth/*` (`credentials: 'include'`) |
| Stored on the server | not stored | SHA-256 hash only |
| Frontend storage | **in memory only** (never `localStorage`) | not readable by JavaScript |

- Each refresh **rotates** the refresh token. Presenting an already-used token
  is treated as theft: every session of that user is revoked.
- Every authenticated request re-checks the account in the database, so
  disabling or deleting an account takes effect immediately.
- Passwords are hashed with BCrypt via Spring's `DelegatingPasswordEncoder`
  (stored as `{bcrypt}$2a$...`), which allows upgrading the algorithm later.
- Logout revokes the refresh token and clears the cookie. An already-issued
  access token stays valid until it expires (≤ 15 min); the frontend simply
  forgets it.

## API contract

Base URL: `http://localhost:8080` locally. All bodies are JSON. Timestamps are
ISO-8601 UTC. IDs are UUIDs.

### `POST /api/auth/register` — public

```json
{ "email": "ada@example.com", "username": "ada_lovelace", "password": "correct-horse-battery" }
```

| Field | Rules |
|---|---|
| `email` | required, valid email, ≤ 254 chars; stored lower-cased, unique case-insensitively |
| `username` | required, 3–30 chars of letters, digits, `-`, `_`, starting and ending with a letter or digit; unique case-insensitively, case preserved |
| `password` | required, 8–72 characters and ≤ 72 bytes UTF-8 |

**201 Created** — the user is logged in immediately (no email verification yet).
Sets the refresh cookie (browser-session cookie).

```http
Set-Cookie: lp_refresh_token=<opaque>; Path=/api/auth; Secure; HttpOnly; SameSite=Lax
Cache-Control: no-store
```
```json
{
  "accessToken": "eyJraWQiOi...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": "080cd446-aec6-4988-a9a7-7a127feecdd8",
    "email": "ada@example.com",
    "username": "ada_lovelace",
    "role": "USER",
    "createdAt": "2026-10-23T17:24:32.101945Z"
  }
}
```

Errors: `400 VALIDATION_FAILED`, `400 MALFORMED_REQUEST`, `409 EMAIL_ALREADY_USED`,
`409 USERNAME_ALREADY_USED`.

### `POST /api/auth/login` — public

```json
{ "email": "ada@example.com", "password": "correct-horse-battery", "rememberMe": true }
```

`rememberMe` is optional (default `false`). With `true` the cookie is persistent
(`Max-Age` ≈ 30 days); otherwise it is a browser-session cookie.

**200 OK** — same body and cookie as register.

Errors: `400 VALIDATION_FAILED`; `401 INVALID_CREDENTIALS` (identical for an unknown
email and a wrong password); `429 ACCOUNT_LOCKED` after
`AUTH_MAX_FAILED_LOGINS` wrong passwords, for `AUTH_LOGIN_LOCK_DURATION`;
`403 ACCOUNT_DISABLED`.

### `POST /api/auth/refresh` — public, uses the cookie

No body. Restores a session after a page reload and renews the access token.

**200 OK** — same body as login, with a **new** refresh cookie (rotation).

**401 INVALID_REFRESH_TOKEN** — no, unknown, expired, revoked or reused cookie;
the response also clears the cookie. Treat it as "logged out".

### `POST /api/auth/logout` — public, uses the cookie

No body. Revokes the session and clears the cookie. Always **204 No Content**
(idempotent; works with an expired access token).

### `GET /api/auth/me` — requires `Authorization: Bearer <accessToken>`

**200 OK**

```json
{
  "id": "080cd446-aec6-4988-a9a7-7a127feecdd8",
  "email": "ada@example.com",
  "username": "ada_lovelace",
  "role": "USER",
  "createdAt": "2026-10-23T17:24:32.101945Z"
}
```

**401 UNAUTHORIZED** — missing, invalid or expired token (header `WWW-Authenticate: Bearer`).

### Every other endpoint

Requires a valid access token; otherwise `401 UNAUTHORIZED`.

### Error format

Every error is RFC 9457 `application/problem+json` with an extra `code` member
(stable — switch on it, not on `detail`) and, for validation errors, `errors`:

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "Request validation failed.",
  "instance": "/api/auth/register",
  "code": "VALIDATION_FAILED",
  "errors": [
    { "field": "email", "message": "must be a well-formed email address" },
    { "field": "password", "message": "must be between 8 and 72 characters" }
  ]
}
```

Codes: `VALIDATION_FAILED`,
`MALFORMED_REQUEST`, `EMAIL_ALREADY_USED`, `USERNAME_ALREADY_USED`,
`INVALID_CREDENTIALS`, `ACCOUNT_LOCKED`, `ACCOUNT_DISABLED`,
`INVALID_REFRESH_TOKEN`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `CONFLICT`,
`INTERNAL_ERROR` (plus HTTP status names such as `METHOD_NOT_ALLOWED` for
framework errors). Stack traces and internal messages are never returned.

## Connecting the Next.js frontend

1. **Origins.** Set `CORS_ALLOWED_ORIGINS` to the frontend origin
   (`http://localhost:3000` is already allowed in `dev`). Exact origin, no trailing slash.
2. **Credentials.** Call `/api/auth/register|login|refresh|logout` with
   `credentials: 'include'` so the browser stores and sends the refresh cookie.
3. **Access token in memory.** Keep `accessToken` in a JS variable / React
   context, never in `localStorage`/`sessionStorage`, and send it as
   `Authorization: Bearer …` on API calls.
4. **Session restore.** On app start (and when the token is about to expire —
   `expiresIn` seconds), call `POST /api/auth/refresh`. `200` → logged in;
   `401` → show the login screen.
5. **Retry once.** If an API call returns `401`, refresh once and retry; if the
   refresh fails, log out locally.
6. **Single-flight refresh.** Never run two refreshes with the same cookie at
   once (e.g. several tabs or parallel requests): the second is treated as token
   reuse and logs the user out everywhere. Share one in-flight refresh promise,
   and coordinate tabs (e.g. Web Locks API or `BroadcastChannel`).
7. **Logout.** `POST /api/auth/logout` with credentials, then drop the
   in-memory token.
8. **Server components / route handlers** that call the API server-to-server
   must forward the `Authorization` header (the refresh cookie is scoped to
   `/api/auth` on the API host and is not meant to be read by the Next.js server).

**Deployment topology.** The default `SameSite=Lax` cookie works when the
frontend and API are on the **same site** — e.g. `app.example.com` and
`api.example.com`, or `localhost:3000` and `localhost:8080`. If they are on
different registrable domains (e.g. `*.vercel.app` + `*.railway.app`), either
put the API behind the frontend's domain (reverse proxy / rewrite, recommended)
or set `AUTH_COOKIE_SAME_SITE=None` (requires HTTPS) and be aware that
third-party-cookie blocking in browsers can break session restore. If you
proxy the API under a different path, update `AUTH_COOKIE_PATH`.

## Security notes and known limitations

- **CSRF:** API calls authenticate with a header, so CSRF protection is
  disabled. The refresh cookie is `HttpOnly`, `SameSite=Lax` and scoped to
  `/api/auth`; with `SameSite=None` a cross-site page could trigger
  refresh/logout (it cannot read the response) — prefer a same-site deployment.
- **No email verification or password reset yet.** Accounts are active on
  registration. Add both before a public launch (needs an email provider).
- **Rate limiting** is limited to the per-account login lock. Add IP-based rate
  limiting (gateway or filter) for `/api/auth/*` before going public. The lock
  also reveals that an account exists after N failed attempts, and registration
  necessarily reveals whether an email is taken.
- **Expired refresh tokens are not purged yet** — add a scheduled cleanup
  (`DELETE FROM refresh_tokens WHERE expires_at < now() - interval '7 days'`).
- **JWT key rotation** is manual: changing `JWT_SECRET` logs out all access
  tokens (refresh tokens keep working). Move to asymmetric keys (RS256/EdDSA)
  if other services ever need to verify tokens.
- Security-relevant events (registration, failed logins, locks, refresh-token
  reuse) are logged on the `security.audit` logger with user IDs only — never
  passwords, tokens or emails.

## Project structure

Layered, the same way as Scrivetta (ebook_writer):

```
src/main/java/com/project/learning_platform/
├── controller/          AuthController (thin: HTTP in, DTO out)
├── service/             AuthService, RefreshTokenService (business logic, transactions)
├── repository/          UserRepository, RefreshTokenRepository (Spring Data JPA)
├── entity/              User, UserRole, UserStatus, RefreshToken
├── dto/                 UserDTO
├── request/             RegisterRequest, LoginRequest (validated input)
├── response/            AuthResponse
├── security/            SecurityConfig, PasswordConfig, JwtProvider, AuthCookies,
│                        UserJwtAuthenticationConverter, AuthenticatedUser, error handler
├── config/properties/   JwtProperties, AuthProperties, CorsProperties (env-driven settings)
├── exceptions/          ErrorCode, ApiException, GlobalExceptionHandler (problem+json)
└── validation/          @MaxUtf8Bytes
src/main/resources/
├── application.yml, application-dev.yml
└── db/migration/        Flyway migrations
src/test/java/...        Testcontainers + MockMvc integration tests, unit tests
```

A new feature adds its classes to these layers: an entity + repository, a
service with the logic, a controller, request/response DTOs, and a Flyway
migration. See [docs/architecture.md](docs/architecture.md).
