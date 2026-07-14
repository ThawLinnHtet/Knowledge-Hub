# Knowledge Hub

Knowledge Hub is a document intelligence platform with a Spring Boot API and a
React/Vite web application. The repository is being implemented in the vertical
slices defined by `docs/sdd-knowledge-hub-platform.md`.

## Prerequisites

- Java 25
- Docker Desktop with Linux containers
- Node.js 24 (used by CI and Playwright tooling)

## Local Infrastructure

Create a root `.env` from `.env.example` if you need to override the safe
development defaults, then start PostgreSQL with pgvector and MinIO:

```powershell
docker compose up -d
docker compose ps
```

PostgreSQL is available on port `5433`. MinIO serves its API on port `9000` and
its development console on port `9001`.

## Backend

Run commands from `knowledge-hub-api`:

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

The backend uses the root `compose.yaml` during local development. Automated
integration tests use an isolated PostgreSQL 16 pgvector Testcontainer and do
not require the root Compose services to be running.

Configuration is read from environment variables documented in `.env.example`.
For local development, the API also imports the gitignored root `.env` when it
is launched from either the repository root or `knowledge-hub-api`. Real secrets
and `.env` files must not be committed. Fake mode disables Spring AI provider
models and does not require an API key. To use OpenRouter, set
`AI_PROVIDER_API_KEY` to a real key, set `AI_FAKE_MODE=false`, and set both
`AI_MODEL_CHAT=openai` and `AI_MODEL_EMBEDDING=openai`.

Local API documentation is available at `http://localhost:8080/swagger-ui.html`.
The `prod` profile disables OpenAPI and automatic Docker Compose startup. Safe
health probes are public at `/actuator/health/liveness` and
`/actuator/health/readiness`; other Actuator endpoints remain authenticated.
Console logs use ECS-compatible structured JSON with request ID, user ID when
authenticated, endpoint, method, response status, latency, and error code.

## Frontend

Run commands from `knowledge-hub-web`:

```powershell
npm install
npm run dev
```

The Vite application runs on `http://localhost:5173` and calls the API URL from
`VITE_API_URL` (default `http://localhost:8080`). Frontend quality gates are:

```powershell
npm run lint
npm run typecheck
npm test
npm run build
```

## End-to-End Smoke Test

The Playwright smoke test starts an isolated PostgreSQL/pgvector and MinIO stack
on ports `55433` and `59000`, launches the API and frontend on dedicated ports
`18080` and `15173`, and runs the full register, upload, ingestion, search, cited
chat, and document deletion journey in deterministic fake-AI mode. The isolated
containers and volumes are removed afterward.

Run commands from `knowledge-hub-web`:

```powershell
npm run e2e:install
npm run e2e
```

Docker Desktop, Java 25, and the frontend dependencies are required. Existing
servers are not reused unless `E2E_REUSE_EXISTING_SERVER=true`; only enable reuse
when both dedicated-port servers are configured for disposable test data.

## Production Profile

Start the packaged API with `--spring.profiles.active=prod` and provide all
secrets through the environment. At minimum, replace the development database,
MinIO, JWT, CORS, cookie, and AI-provider values from `.env.example`. Production
deployments should set `AUTH_SECURE_COOKIES=true`, use TLS, provision a
prefix-restricted MinIO application key, and keep `AUTH_LOG_RESET_TOKENS=false`.
Production startup fails when it detects local service URLs, development
credentials, insecure cookies, reset-token logging, fake AI, disabled provider
models, missing SMTP/provider configuration, or an omitted explicit
`REGISTRATION_ENABLED` policy. It also requires `sslmode=verify-full` in
`DATABASE_URL`, an HTTPS AI provider URL, and both `SMTP_STARTTLS_ENABLED=true`
and `SMTP_STARTTLS_REQUIRED=true`.

## Continuous Integration

`.github/workflows/ci.yml` runs backend Maven verification, frontend lint,
typecheck, formatting, unit tests and build, then the Playwright critical-flow
smoke test. Browser traces, screenshots, and video are uploaded when E2E fails.

Docker Compose and local API runs both read the root `.env` file. The `test` and
`prod` profiles do not import it; use process environment variables or a secret
manager for production.
