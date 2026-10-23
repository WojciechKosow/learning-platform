# Architecture notes

How the foundation is meant to grow, and the boundaries that must hold as it
does.

## Code layout

Layered packages, as in Scrivetta: `controller` → `service` → `repository` →
`entity`, with `request` / `response` / `dto` at the API edge, plus `security`,
`config/properties` and `exceptions`. Controllers stay thin, entities never
leave the service layer (map them to DTOs), dependencies are
constructor-injected.

| Area | Status | Main classes / tables |
|---|---|---|
| Accounts | done | `User`, `UserRepository` (`users`) |
| Authentication | done | `AuthController`, `AuthService`, `RefreshTokenService` (`refresh_tokens`) |
| Security | done | `SecurityConfig`, `JwtProvider`, `UserJwtAuthenticationConverter`, CORS |
| Errors | done | `ErrorCode`, `ApiException`, `GlobalExceptionHandler` |
| Learner profile | next | goals, preferred track, onboarding progress |
| Assessment | planned | AI assessments, questions, practical tasks, results |
| Skills | planned | skill catalogue and per-user skill profile (verified / unverified) |
| Projects / tasks | planned | tracks, projects, tasks, submissions |
| Mentoring | planned | AI help sessions with tiered assistance (hint → explanation → example → direct help) |
| Execution | planned | client for the isolated execution service (see below) |
| GitHub | planned | GitHub connection and approved repository actions (see below) |
| Billing | planned | subscriptions and usage tracking |

If the number of features grows large, grouping by feature (one package per
area, each with its own layers) is an easy, mechanical move later.

### Keeping `User` small

`users` holds only identity, credentials and account state. Everything else is
its own table referencing `users(id)`:

```sql
-- example for the next step (not created yet)
CREATE TABLE learner_profiles (
    user_id                 UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    preferred_track         VARCHAR(40),          -- e.g. JAVA_SPRING, TS_REACT_NEXT
    onboarding_completed_at TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL
);
```

Tracks should become reference data (a table or enum) rather than free text so
that assessments, skills and projects can point at them.

### Authorization

`AuthenticatedUser` (inject with `@AuthenticationPrincipal`) carries the user id
and role. Per-resource checks (does this submission belong to the caller?)
belong in services; role checks can use `@PreAuthorize("hasRole('ADMIN')")`
(method security is enabled).

## Boundary: running user code

User-submitted code **must never run inside this Spring Boot process or on its
host** — no `ProcessBuilder`, `javax.script`, in-process compilers or Docker
socket access from this service.

Planned shape:

```
Next.js ──> API (this app) ──(job: repo snapshot + command + limits)──> queue
                                                                        │
                         results (stdout, test report, exit code) <── Execution workers
                                                                     (separate service, separate hosts)
```

- The API only creates execution jobs and stores results: an
  `ExecutionService` client in `service/` plus job/result entities.
- Workers run each job in a fresh, disposable sandbox (gVisor/Firecracker or an
  equivalent hardened container runtime): non-root, no host mounts, no network
  by default (or an allow-list for dependency mirrors), CPU/memory/disk/process
  and wall-clock limits, destroyed after the run.
- Workers never receive platform secrets, database credentials or GitHub
  tokens. Results are size-limited and treated as untrusted input (escaped
  when rendered, never executed or interpreted by the API).

## Boundary: GitHub integration

Not implemented yet. Requirements for when it is:

- Prefer a **GitHub App** (fine-grained, per-repository installation, short-lived
  installation tokens) over OAuth/PAT tokens with broad scopes.
- A `github_connections` table references `users(id)`. Any long-lived token is
  stored **encrypted** (AES-GCM with a key from the environment/KMS, never
  committed), never logged, never returned by the API, and deleted on
  disconnect.
- Reading a repository the user selected is fine within granted scope.
  **Consequential actions** — creating branches, pushing commits, opening pull
  requests, commenting — are only executed after an explicit, per-action user
  approval recorded server-side (who approved what diff, when). AI-generated
  changes are proposals until approved.
- The execution sandbox gets a repository snapshot, not credentials.

## Data safety

- Schema changes only through Flyway migrations; `ddl-auto` stays `validate`.
- Tests use Testcontainers; nothing in the app seeds or deletes real user data.
- Development defaults live only in the `dev` profile; production fails fast on
  missing secrets.
