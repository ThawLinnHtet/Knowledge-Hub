# SDD: Knowledge Hub Platform

## Source PRD

Source PRD: GitHub issue https://github.com/ThawLinnHtet/Knowledge-Hub/issues/1 and local document `docs/prd-knowledge-hub-platform.md`.

Knowledge Hub is a production-quality personal/team document intelligence platform. Authenticated users have private knowledge bases where they upload documents, organize them into collections, search with hybrid retrieval, and chat with source-backed answers using RAG. The first version intentionally excludes enterprise SaaS features such as multi-tenancy, billing, RBAC, audit logs, connectors, workflow automation, and multi-region scaling.

## Spec Summary

Implementation should proceed as a monorepo build-out from the current Spring Boot scaffold. The backend becomes `knowledge-hub-api`, organized by feature modules with DTOs, MapStruct mappers, Flyway migrations, JPA repositories, Testcontainers integration tests, fake AI mode, and explicit service boundaries for storage, ingestion, retrieval, and chat providers. The frontend becomes `knowledge-hub-web`, a React/Vite dashboard application using TanStack Query for server state, shadcn/ui for accessible components, Zustand only for local UI/session preferences, and Playwright/Vitest coverage for critical flows.

The implementation should be slice-based and TDD-driven. Each slice must deliver a user-observable vertical capability, backed by tests at the highest practical seam. Paid AI calls are not required for local or CI verification because deterministic fake chat and embedding clients are part of the platform design.

## Product Specs

PS-01 Authentication and Account Lifecycle

Acceptance criteria:

- Users can register with email and password when registration is enabled.
- Registration can be disabled by configuration and returns the standard error contract when disabled.
- Users can log in and receive a short-lived access token plus an HttpOnly refresh-token cookie.
- Refresh tokens are rotated on use and stored hashed with expiry, revocation metadata, and session/device metadata.
- Cookie-backed refresh and logout endpoints use CSRF protection; normal APIs use bearer access tokens.
- Users can request password reset. Production sends a reset link through SMTP; local/dev without SMTP logs the reset link/token only under safe development configuration.
- Password reset tokens are short-lived, single-use, stored hashed, and rejected after expiry or use.
- Authentication endpoints are rate-limited with temporary cooldowns for abusive login, registration, refresh, forgot-password, and reset-password attempts.
- Users can delete their account only after password re-entry and typed confirmation.
- Account deletion immediately revokes sessions/tokens, marks the account deletion-pending, and runs background cleanup for all user-owned database and MinIO data.

Edge and failure cases:

- Duplicate email registration returns a safe standard error without exposing sensitive account state.
- Invalid credentials use a generic auth error.
- Expired, reused, revoked, or malformed refresh/reset tokens are rejected.
- Deletion-pending users cannot authenticate or access APIs.

Permission rules:

- All non-auth APIs require a valid bearer access token.
- Every user-owned query is scoped to the authenticated user.

PS-02 Document Upload, Storage, Validation, and Duplicate Handling

Acceptance criteria:

- Authenticated users can upload multiple PDF, DOCX, TXT, and Markdown files in one action.
- Backend validates file extension, detected content type, and configured size limits.
- Unsupported, mismatched, or oversized files return per-file standard errors.
- SHA-256 hash is computed before finalizing storage and used to detect same-user duplicates.
- Duplicate uploads use a two-step confirmation flow with short-lived, hashed, single-use confirmation tokens tied to the user, hash, filename, size, and collection.
- Confirmed uploads store originals in private MinIO buckets using opaque generated object keys.
- Users can request backend-authorized short-lived pre-signed URLs to download original files.

Edge and failure cases:

- A batch upload can partially succeed while reporting per-file failures.
- Duplicate files can be skipped or explicitly uploaded anyway.
- Invalid confirmation tokens are rejected.
- MinIO failures leave no ready document and return or record a clear storage error.

Permission rules:

- Users can only upload to and download from their own knowledge base.
- MinIO objects are never public.

PS-03 Collections and Document Library

Acceptance criteria:

- Users can create, list, rename, and delete collections.
- Each user has an Uncategorized fallback collection.
- Each document belongs to one collection initially.
- Deleting a collection moves its documents to Uncategorized rather than deleting them.
- Users can list documents with status, collection, type, uploaded date, and processing diagnostics.
- Users can view document detail with metadata, diagnostics, chunks/snippets, citations, and original download action.
- Users can permanently delete documents. Deletion removes MinIO object, chunks, embeddings, search data, and document metadata.
- Existing chat citations to deleted documents render as source deleted without exposing removed content.

Edge and failure cases:

- Users cannot delete or access collections/documents owned by another user.
- Deleting Uncategorized is rejected.
- Document deletion handles missing MinIO objects idempotently while removing database data.

PS-04 Asynchronous Ingestion and Processing Diagnostics

Acceptance criteria:

- Uploaded documents become durable pending records before background processing.
- An in-process Spring worker polls PostgreSQL and claims eligible documents using lock/lease fields.
- Ingestion extracts text with Apache Tika behind a `DocumentTextExtractor` boundary.
- PDF page numbers are captured when feasible; otherwise chunks use section or chunk-position metadata.
- Chunking is structure-aware for Markdown/DOCX where possible and configurable character-based with overlap as fallback.
- Chunks store text, order, page/section/position metadata, character counts, token estimates where available, full-text search data, embedding metadata, and pgvector embeddings.
- Statuses expose pending, processing, ready, and failed to users.
- Internal failure metadata stores failure code, message, retry count, next retry time, and retryability.
- Transient failures retry automatically up to a configured cap.
- Users can manually retry failed documents.

Edge and failure cases:

- Permanent failures such as unsupported content or configured limit violations become failed with clear reasons and no automatic endless retry.
- Expired locks can be reclaimed safely.
- Reprocessing replaces stale chunks/search data atomically enough that users never see mixed old/new ready content.

PS-05 AI Providers, Embeddings, Cost Controls, and Fake AI Mode

Acceptance criteria:

- Chat uses OpenRouter's OpenAI-compatible API, configured through backend settings, with `google/gemini-2.5-flash-lite` as the initial chat model.
- Embeddings use OpenRouter's OpenAI-compatible API, configured through backend settings, with `nvidia/llama-nemotron-embed-vl-1b-v2:free` as the initial embedding model.
- The UI does not allow users to change provider or model initially.
- One embedding dimension is active per deployment and enforced by schema/configuration.
- Vector similarity uses cosine distance and HNSW indexing by default.
- Hard backend limits enforce upload size, extracted character count, chunk count, retrieved chunk count, and chat/prompt token budget.
- Deterministic fake AI mode provides fake embeddings and predictable cited chat responses for local development, automated tests, and CI without API keys.

Edge and failure cases:

- Missing provider credentials in real-provider mode produces clear configuration errors.
- Provider timeouts/rate limits map to standard provider errors.
- Limit violations produce standard quota/limit errors before expensive work continues.

PS-06 Hybrid Search and Retrieval

Acceptance criteria:

- User-facing search combines semantic vector search and PostgreSQL full-text keyword search.
- RAG retrieval uses the same hybrid retrieval foundation.
- Search filters support collection scope, selected documents, document type, and uploaded date.
- RAG retrieval enforces authenticated user ownership, document status ready, and selected chat scope.
- Search results include document/chunk metadata, snippets, relevance information, and source navigation data.

Edge and failure cases:

- Empty queries and empty result sets render useful frontend states.
- Documents that are pending, processing, failed, or deleted are excluded from RAG retrieval.
- Model-based reranking is not implemented but the retrieval boundary allows it later.

PS-07 RAG Chat, Streaming, Grounding, and Citations

Acceptance criteria:

- Chat is a top-level workspace with saved sessions.
- Users can create, list, rename, revisit, and delete chat sessions.
- Chat defaults to all ready documents but supports session scope by collection or selected documents.
- Scope can be changed before sending a message and the scope used is stored per message.
- Each user message retrieves fresh document context while recent conversation history supports follow-up questions.
- Assistant responses stream over SSE and persist the final assistant message after completion.
- Answers cite specific chunks with page, section, or chunk position when available.
- Citations are stored as structured records linked to assistant messages with document ID, chunk ID, title/filename snapshot, location metadata, relevance score, and source-deleted state.
- Chat messages do not store full retrieved chunk text snapshots.
- If retrieval evidence is weak or absent, the assistant refuses or clearly says the answer is not found in uploaded documents.
- Retrieved document chunks are treated as untrusted evidence, not instructions. The RAG prompt defends against prompt injection inside documents.

Edge and failure cases:

- Interrupted streams leave the UI in a recoverable failed state and do not save partial assistant messages as complete answers.
- Deleted source documents render historical citations as source deleted.
- Users cannot access or stream another user's chat sessions.

PS-08 Frontend Dashboard and User Experience

Acceptance criteria:

- React/Vite frontend uses standalone auth pages and an authenticated dashboard shell.
- The public root route presents the private document, hybrid search, and cited-answer value proposition with registration as the primary action and sign-in as secondary; authenticated visitors are routed to Documents.
- Dashboard navigation includes Documents, Collections, Search, Chat, and Settings.
- Global quick search is available from the shell and a full Search page supports filters/results.
- Documents page shows upload batch progress, status badges, processing diagnostics, and filters for processing, failed, and ready.
- Chat page shows sessions, streaming messages, scope controls, source citation chips/cards, and source navigation.
- Settings shows account/session controls and read-only non-secret system/model/limit status.
- Error states render the standardized backend error message and code where useful.
- UI is responsive for desktop and mobile and uses accessible shadcn/ui components customized to Knowledge Hub's visual identity.

Edge and failure cases:

- Expired access tokens trigger refresh flow and retry where safe.
- Logged-out or deletion-pending users are routed to auth pages.
- Loading, empty, failed, and partial-success states are visible for upload, search, ingestion, and chat.

Accessibility requirements:

- Forms have labels and validation messages.
- Dialogs, dropdowns, navigation, search, and chat controls are keyboard accessible.
- Streaming status and upload progress are announced or represented accessibly.

PS-09 API, Error Contract, Configuration, Observability, and Documentation

Acceptance criteria:

- Backend APIs are versioned under `/api/v1`.
- Resources use RESTful endpoints for auth, documents, collections, search, chat history, settings/status, and account lifecycle.
- Sending a chat message uses a command-style SSE endpoint.
- Responses use DTOs and never expose JPA entities directly.
- Standard JSON error contract includes machine-readable code, human-readable message, request/correlation ID, field errors where applicable, and safe metadata.
- Global exception handling maps validation, auth, ownership, not found, duplicate upload, processing, quota/limit, provider, and storage errors.
- OpenAPI/Swagger docs are enabled in local/dev and disabled or explicitly protected/configurable in production.
- Actuator exposes safe health/readiness/liveness and basic metrics.
- Structured JSON logs include request ID, user ID when authenticated, endpoint, latency, status, error code, provider/retrieval metadata, and never log sensitive payloads by default.
- CORS uses configured allowed origins, not wildcards.
- Secrets and environment-specific settings come from profiles/environment variables.
- Safe `.env.example` and setup documentation exist for backend, frontend, and infrastructure.

PS-10 Data Model, Persistence, and Schema Governance

Acceptance criteria:

- PostgreSQL stores users, sessions, tokens, password reset tokens, upload confirmation tokens, collections, documents, chunks, chats, messages, citations, account deletion jobs, and processing metadata.
- pgvector extension and HNSW cosine indexes support chunk vector search.
- PostgreSQL full-text search supports keyword retrieval over chunks and relevant document metadata.
- UUIDs are used for user-facing/domain primary keys, defaulting to PostgreSQL `gen_random_uuid()`.
- Major domain tables include `created_at` and `updated_at`; lifecycle timestamps are included only where meaningful.
- Flyway SQL migrations create and evolve schema manually.
- Hibernate validates schema in non-test environments.
- DTO/entity mapping uses MapStruct for straightforward mapping.

PS-11 Verification, CI, Formatting, and Quality Gates

Acceptance criteria:

- Backend tests prioritize Testcontainers integration coverage for migrations, PostgreSQL/pgvector behavior, repositories, services, auth, ingestion persistence, and API behavior.
- Backend unit tests cover isolated validation, chunking, token budgeting, cost limits, fake AI behavior, and mapper behavior.
- Frontend tests use Vitest and React Testing Library for critical auth, upload, search, chat streaming, settings, and error-rendering behavior.
- Playwright E2E covers register/login, upload documents, wait for processing, search, chat with citations, and delete document.
- CI runs backend tests, frontend lint/typecheck/tests, and Playwright smoke tests using fake AI mode when the relevant app exists.
- CI is required before merge.
- Backend formatting/static analysis is Maven-enforced with practical defaults.
- Frontend TypeScript strict mode, ESLint, and Prettier are enforced.

Out-of-scope behavior:

- Multi-tenant SaaS, billing, RBAC, audit logs, connectors, workflow automation, multi-region scaling, mandatory email verification, Keycloak/OIDC login, user-selectable model UI, model reranking, OCR, spreadsheet/slides/HTML/ZIP/web imports, full rich document preview, antivirus implementation, application-level file encryption, full export packages, admin health UI, general-knowledge fallback by default, and public production Swagger UI are not part of this implementation.

## Current Architecture

- Repository root currently contains `AGENTS.md`, `docs/prd-knowledge-hub-platform.md`, and a minimal Spring Boot scaffold under `spring-rag`.
- The scaffold uses Maven, Java 25, Spring Boot 4.1.0, Spring AI 2.0.0, Spring Web MVC, Spring Data JPA, Flyway, OAuth2 resource server, Spring AI OpenAI model starter, Spring AI pgvector starter, Docker Compose support, Lombok, and Testcontainers.
- The only application class is `com.example.spring_rag.SpringRagApplication`.
- Existing test coverage is a context-load test using a Testcontainers PostgreSQL container with `pgvector/pgvector:pg16`.
- Existing `compose.yaml` only starts a PostgreSQL/pgvector service with scaffold credentials.
- No React frontend exists yet.
- No Flyway migrations, JPA entities, controllers, services, API DTOs, MapStruct mappers, MinIO integration, auth implementation, ingestion worker, search implementation, chat implementation, OpenAPI docs, Actuator configuration, CI, or root infrastructure Compose exists yet.
- No ADRs or prior specs were found in the repository.
- Existing package/artifact names are scaffold defaults and should be replaced by production Knowledge Hub naming.

## Technical Specs

Backend modules:

- `common`: error contract, exception handling, request IDs, security helpers, pagination, timestamps, configuration properties, logging support.
- `auth`: registration, login, refresh, logout, password reset, refresh token sessions, rate limiting, CSRF for cookie-backed endpoints.
- `users`: account profile, account deletion request, deletion job orchestration.
- `collections`: collection CRUD and Uncategorized fallback.
- `documents`: upload validation, duplicate preflight, confirmation tokens, metadata, downloads, document deletion, detail/list APIs.
- `storage`: MinIO bucket management, opaque object keys, pre-signed downloads, object deletion.
- `ingestion`: database-backed worker, document claiming, extraction, chunking, embedding, retry/failure metadata.
- `ai`: chat client abstraction, embedding client abstraction, OpenRouter/OpenAI-compatible provider configuration, deterministic fake AI clients.
- `search`: hybrid retrieval over pgvector and PostgreSQL full-text search, filters, score normalization.
- `chat`: chat sessions, messages, SSE streaming, RAG prompt assembly, citations, source-deleted rendering.
- `settings`: read-only safe system/model/limit status.
- `observability`: Actuator exposure, health indicators, structured logging metadata.

Frontend modules:

- App shell: routing, auth guard, dashboard layout, responsive navigation, global quick search.
- API client: Axios instance, token refresh handling, error contract parsing, SSE handling.
- Auth: login, register, forgot password, reset password, logout/session state.
- Documents: upload flow, duplicate confirmation, list filters, status badges, detail view, retry, delete, download.
- Collections: create, rename, delete, document reassignment to Uncategorized behavior.
- Search: full search filters, results, snippets, source navigation.
- Chat: sessions, scope controls, streaming response, citations, source-deleted state.
- Settings: account/session controls, read-only system status, account deletion.
- Shared UI: shadcn/ui components, toasts, dialogs, forms, empty/loading/error states.

Primary API surface:

| Area        | Endpoint shape                             | Behavior                                               |
| ----------- | ------------------------------------------ | ------------------------------------------------------ |
| Auth        | `POST /api/v1/auth/register`               | Create account when enabled                            |
| Auth        | `POST /api/v1/auth/login`                  | Issue access token and refresh cookie                  |
| Auth        | `POST /api/v1/auth/refresh`                | Rotate refresh token and issue new access token        |
| Auth        | `POST /api/v1/auth/logout`                 | Revoke current refresh token/session                   |
| Auth        | `POST /api/v1/auth/forgot-password`        | Create reset token and send/log reset link             |
| Auth        | `POST /api/v1/auth/reset-password`         | Consume reset token and set new password               |
| Collections | `/api/v1/collections`                      | CRUD user collections                                  |
| Documents   | `POST /api/v1/documents/uploads/preflight` | Validate/hash duplicate preflight                      |
| Documents   | `POST /api/v1/documents/uploads`           | Store confirmed files and create pending docs          |
| Documents   | `/api/v1/documents`                        | List/filter documents                                  |
| Documents   | `/api/v1/documents/{id}`                   | Detail/delete document                                 |
| Documents   | `POST /api/v1/documents/{id}/retry`        | Manual ingestion retry                                 |
| Documents   | `POST /api/v1/documents/{id}/download-url` | Authorized pre-signed download URL                     |
| Search      | `GET /api/v1/search`                       | Hybrid search with filters                             |
| Chats       | `/api/v1/chats`                            | Create/list/rename/delete sessions                     |
| Chats       | `/api/v1/chats/{id}/messages`              | Read persisted messages/citations                      |
| Chats       | `POST /api/v1/chats/{id}/messages:stream`  | Send user message and stream assistant answer over SSE |
| Settings    | `GET /api/v1/settings/system`              | Read-only safe platform configuration                  |
| Account     | `DELETE /api/v1/account`                   | Confirm account deletion and enqueue cleanup           |

Standard error response shape:

```json
{
  "code": "DOCUMENT_LIMIT_EXCEEDED",
  "message": "The document exceeds the configured maximum size.",
  "requestId": "...",
  "fieldErrors": [],
  "metadata": {}
}
```

Core document state machine:

```text
PENDING -> PROCESSING -> READY
PENDING -> PROCESSING -> FAILED
FAILED -> PENDING through manual retry or scheduled retry when retryable
```

Core schema groups:

- Auth/account tables: users, refresh_tokens, password_reset_tokens, upload_confirmation_tokens, account_deletion_jobs.
- Document tables: collections, documents, document_chunks.
- Search fields: chunk embedding vector, chunk full-text vector, metadata indexes, HNSW cosine index.
- Chat tables: chat_sessions, chat_messages, message_citations.
- Operational fields: UUID primary keys, user ownership foreign keys, timestamps, failure metadata, processing locks, retry fields.

Data flow:

1. User registers/logs in and receives bearer access token plus refresh cookie.
2. User uploads documents through preflight and confirmed upload.
3. Backend validates, hashes, stores originals privately, creates pending document records.
4. Ingestion worker claims pending records, extracts text, chunks, embeds, writes search data, and marks ready or failed.
5. Search endpoint runs hybrid retrieval over ready chunks scoped to user and filters.
6. Chat stream endpoint stores user message, retrieves fresh context, streams grounded answer, stores final assistant message and structured citations.
7. Deletion flows hard-delete user-triggered documents/chats or enqueue full account cleanup.

Configuration:

- Environment variables/profiles configure database, MinIO, JWT keys, cookie settings, SMTP, CORS, registration, OpenRouter/OpenAI-compatible provider URL/key, chat model `google/gemini-2.5-flash-lite`, embedding model `nvidia/llama-nemotron-embed-vl-1b-v2:free`, embedding dimension, limits, fake AI mode, OpenAPI exposure, and logging payload debug flag.

## Spec Traceability

| Product spec                                                               | PRD user stories covered                                                     | Technical change                                                                                      | Verification                                                                                                                              |
| -------------------------------------------------------------------------- | ---------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| PS-01 Authentication and Account Lifecycle                                 | US1, US2, US3, US4, US5, US6, US7, US42, US43, US44                          | Auth/users modules, token tables, reset flow, rate limiter, account deletion job                      | Auth API integration tests, token repository tests, rate-limit tests, account deletion job tests, frontend auth tests, E2E register/login |
| PS-02 Document Upload, Storage, Validation, and Duplicate Handling         | US8, US9, US10, US11, US12, US13, US14                                       | Documents/storage modules, MinIO integration, upload preflight/confirm APIs, confirmation token table | Upload API integration tests, storage fake/MinIO tests, validation tests, duplicate flow frontend tests                                   |
| PS-03 Collections and Document Library                                     | US15, US16, US17, US18, US19, US22, US23, US24                               | Collections/documents modules, document detail/delete APIs, citation source-deleted handling          | Collection/document API tests, delete cascade tests, frontend documents tests                                                             |
| PS-04 Asynchronous Ingestion and Processing Diagnostics                    | US20, US21                                                                   | Ingestion worker, extraction/chunking boundaries, processing locks, retry/failure metadata            | Worker integration tests, chunking unit tests, retry/lock tests, document status UI tests                                                 |
| PS-05 AI Providers, Embeddings, Cost Controls, and Fake AI Mode            | US46, US49                                                                   | AI abstraction, fake AI profile, embedding config, hard limit enforcement                             | Fake AI tests, limit tests, provider configuration tests, CI without real API keys                                                        |
| PS-06 Hybrid Search and Retrieval                                          | US25, US26, US27, US28                                                       | Search module, pgvector and full-text SQL, filters, retrieval boundary                                | Search integration tests with seeded chunks, frontend search tests                                                                        |
| PS-07 RAG Chat, Streaming, Grounding, and Citations                        | US29, US30, US31, US32, US33, US34, US35, US36, US37, US38, US39, US40, US41 | Chat module, SSE endpoint, prompt assembly, retrieval integration, citation persistence               | Chat API/SSE tests with fake AI, prompt-grounding tests, frontend streaming tests, E2E chat with citations                                |
| PS-08 Frontend Dashboard and User Experience                               | US47, US48, US53                                                             | React app, public landing, dashboard shell, pages, shadcn/ui components, TanStack Query/Zustand boundaries | Frontend unit/integration tests, accessibility checks, responsive manual review, Playwright smoke                                      |
| PS-09 API, Error Contract, Configuration, Observability, and Documentation | US45, US50, US51, US52                                                       | Common error handling, OpenAPI, Actuator, CORS/config/env docs, structured logging                    | Error contract API tests, OpenAPI availability tests, health endpoint tests, log metadata tests                                           |
| PS-10 Data Model, Persistence, and Schema Governance                       | PRD implementation decisions 159-163 and persistence requirements            | Flyway migrations, JPA entities, MapStruct mappers, schema validation                                 | Flyway/Testcontainers migration tests, Hibernate validate startup test, mapper tests                                                      |
| PS-11 Verification, CI, Formatting, and Quality Gates                      | PRD testing decisions 175-189                                                | CI workflows, backend/frontend test tooling, formatting/static checks, Playwright setup               | CI run, Maven verification, frontend lint/typecheck/test commands, E2E smoke                                                              |

## Implementation Plan

1. Repository and backend identity foundation

Product specs: PS-09, PS-10, PS-11.

Likely changes: rename `spring-rag` to `knowledge-hub-api`, base package to Knowledge Hub naming, root monorepo layout, root infrastructure Compose, Maven dependencies for MapStruct, Tika, MinIO, OpenAPI, Actuator, formatting/static checks, test profile cleanup, `.env.example`, setup docs.

Verification gate: backend context-load test passes with Testcontainers; Maven formatting/check/test command passes; root docs describe local setup.

Always use Lombok in both entity and service layer to reduce boilerplate

2. Database foundation and common backend contracts

Product specs: PS-09, PS-10.

Likely changes: Flyway baseline migration for extensions and core tables, standard error response, global exception handler, request IDs, schema validation config, CORS config, environment properties.

Verification gate: migration integration test proves pgvector/full-text prerequisites and UUID defaults; API error contract test passes; app starts with `ddl-auto=validate`.

3. Authentication and sessions

Product specs: PS-01.

Likely changes: auth/users modules, user entity/repository, password hashing, register/login/refresh/logout endpoints, hashed refresh tokens, CSRF on refresh/logout, registration config, rate limiting.

Verification gate: auth API integration tests cover register/login/refresh rotation/logout/rate limit/disabled registration; no protected endpoint works without bearer token.

4. Password reset and account deletion lifecycle

Product specs: PS-01.

Likely changes: reset token table/service, SMTP/dev logger mail boundary, account deletion job, token revocation, cleanup orchestration boundary.

Verification gate: reset request/consume/expiry/reuse tests pass; account deletion marks pending, revokes sessions, and invokes cleanup job in tests.

5. Storage and document upload preflight

Product specs: PS-02.

Likely changes: storage module, MinIO client config, document metadata table/entities, upload validation, SHA-256 hashing, duplicate preflight endpoint, confirmation token table/service.

Verification gate: upload validation and duplicate preflight integration tests pass using fake/minimal storage seam; invalid files return per-file standard errors.

6. Confirmed upload, collections, document library, and downloads

Product specs: PS-02, PS-03.

Likely changes: collection APIs, Uncategorized creation, confirmed multi-file upload endpoint, MinIO object writes, document list/detail/delete/retry/download URL endpoints.

Verification gate: API tests cover multi-file partial success, collection delete moves docs to Uncategorized, document delete removes owned data, pre-signed URL authorization.

7. Ingestion extraction, chunking, embeddings, and diagnostics

Product specs: PS-04, PS-05, PS-10.

Likely changes: ingestion module, worker polling/claiming/locking, Tika extractor, chunker, fake embedding client, chunk table/vector writes, retry/failure metadata, cost limits.

Verification gate: worker tests process pending doc to ready with chunks; transient failure retries; permanent limit failure becomes failed; chunking unit tests pass.

8. Hybrid search

Product specs: PS-06.

Likely changes: search module, SQL/native queries for vector and full-text retrieval, score normalization, filters, search DTOs/API.

Verification gate: Testcontainers search tests prove keyword-only, semantic-only, hybrid, filter, and ownership/status behavior.

9. RAG chat and SSE streaming

Product specs: PS-07.

Likely changes: chat sessions/messages/citations schema and APIs, retrieval integration, prompt assembly, fake chat client, SSE stream endpoint, grounded refusal behavior, prompt injection guard.

Verification gate: chat integration tests cover session CRUD, scoped retrieval, streamed answer, citation persistence, weak-evidence refusal, deleted source citation rendering.

10. Frontend foundation and auth UI

Product specs: PS-01, PS-08, PS-09, PS-11.

Likely changes: create `knowledge-hub-web`, Vite React/TypeScript strict, Tailwind/shadcn/ui, React Router, Axios, TanStack Query, Zustand, auth pages, token refresh handling, ESLint/Prettier/Vitest.

Verification gate: frontend lint/typecheck/test passes; auth form tests cover validation, login success/error, refresh/logout behavior with mocked API.

11. Frontend documents, collections, search, and settings

Product specs: PS-02, PS-03, PS-06, PS-08, PS-09.

Likely changes: public landing page, dashboard shell, Documents, Collections, Search, Settings pages, upload flow with duplicate confirmation, diagnostics, document detail, source/download actions, system status.

Verification gate: React Testing Library tests cover the public landing conversion route, upload states, duplicate decision UI, document status/detail/delete, search filters/results, settings read-only status.

12. Frontend chat and citations

Product specs: PS-07, PS-08.

Likely changes: Chat page, session list, scope selector, SSE client, streaming rendering, citation chips/cards, source-deleted state.

Verification gate: frontend tests cover streaming success/failure, citation rendering, scope changes, session deletion.

13. E2E, CI, observability, and final hardening

Product specs: PS-09, PS-11.

Likely changes: GitHub Actions workflows, Playwright smoke suite, actuator/health assertions, OpenAPI exposure profile, structured JSON logging config, final setup docs.

Verification gate: CI-equivalent local commands pass; Playwright covers register/login, upload, processing, search, chat with citations, delete document using fake AI.

## Verification Specs

Backend verification:

- Use Testcontainers PostgreSQL/pgvector for migration, repository, API, search, and ingestion tests.
- Use deterministic fake AI for all automated chat/embedding behavior.
- Test REST APIs through Spring MVC/Web MVC test seams where possible, validating HTTP status, response DTOs, cookies, CSRF behavior, and standard error contract.
- Test ingestion worker behavior against real database state, including lock claiming, retry metadata, permanent failure, and ready status.
- Test hybrid search with seeded chunks and embeddings in PostgreSQL/pgvector.
- Test MapStruct mappings for API-relevant DTO/entity transformations.

Frontend verification:

- Use Vitest and React Testing Library with mocked API/SSE seams for auth, upload, documents, search, chat, settings, error handling, and loading/empty states.
- Use TypeScript strict mode and linting to catch contract mistakes.
- Use accessibility assertions where practical for forms, dialogs, and navigation.

E2E verification:

- Use Playwright with fake AI mode and local infrastructure.
- Cover the critical happy path: register, login, upload supported document, observe processing ready, search for content, chat and receive citations, delete document.
- Keep E2E limited to stable core flows; edge cases remain in backend/frontend tests.

Manual verification:

- Perform responsive layout review on desktop and mobile because visual quality and responsive behavior cannot be fully proven by unit tests.
- Perform optional real-provider smoke test only when API keys are configured, not in default CI.

Existing prior art:

- Current backend has a context-load Testcontainers test using `pgvector/pgvector:pg16`; expand from this seam rather than replacing it.

## Migration And Rollout

- This is a greenfield rollout from scaffold state, so no production backfill is required.
- Flyway migrations should start with extension setup and base tables.
- pgvector embedding dimension is fixed per deployment; changing embedding model/dimension later requires a controlled re-embedding migration/job outside this initial rollout.
- Local infrastructure Compose should move to the repo root and include PostgreSQL/pgvector and MinIO.
- Real `.env` files remain ignored; only safe examples are committed.
- Production Swagger UI is disabled or protected by configuration.
- Fake AI mode is the default for tests/CI; real-provider mode is enabled only by explicit profile/configuration.
- Rollout order should follow implementation slices so each slice has a passing verification gate before the next begins.

## Risks And Tradeoffs

- The PRD is broad for one implementation effort. Slice-based delivery is required to avoid a large unverified rewrite.
- Spring Boot 4.1.0 and Java 25 are newer stack choices; dependency compatibility should be checked as features are added.
- OpenRouter's OpenAI-compatible API is the initial real-provider integration for both chat and embeddings. If the selected embedding model becomes unavailable or changes dimensions, the embedding provider boundary and fixed-dimension migration strategy mitigate rollout risk.
- pgvector HNSW and full-text hybrid scoring require tuning. Initial tests should focus on deterministic correctness, not perfect ranking.
- Apache Tika may not produce perfect PDF page metadata. The spec allows fallback to chunk position.
- SSE streaming is simpler than WebSockets but needs careful frontend retry/failure UX.
- HttpOnly refresh cookies improve security but introduce CSRF/CORS/cookie configuration complexity.
- Application-level encryption and antivirus scanning are deferred, which is acceptable for the scoped personal/team platform but should be revisited for sensitive public deployments.
- Hard delete simplifies privacy but removes recovery options. UI confirmations must be clear.
- Account deletion as a background cleanup job requires idempotent cleanup and good failure visibility.

## Agent Handoff

Start with implementation slice 1: repository and backend identity foundation.

Key starting files:

- `spring-rag/pom.xml`
- `spring-rag/src/main/java/com/example/spring_rag/SpringRagApplication.java`
- `spring-rag/src/test/java/com/example/spring_rag/SpringRagApplicationTests.java`
- `spring-rag/src/test/java/com/example/spring_rag/TestcontainersConfiguration.java`
- `spring-rag/compose.yaml`
- `docs/prd-knowledge-hub-platform.md`
- `docs/sdd-knowledge-hub-platform.md`

Expected first work:

- Rename/restructure the backend to `knowledge-hub-api` with production package naming.
- Add or adjust Maven dependencies and plugins needed for the foundation only.
- Move infrastructure Compose toward a root-level PostgreSQL/pgvector and MinIO setup.
- Keep the first test seam simple: context load with Testcontainers and schema/config validation.

Definition of done for slice 1:

- Backend project has Knowledge Hub naming.
- App starts under the new package.
- Existing context-load test passes.
- Maven verification command passes.
- No feature implementation beyond foundation setup is included.

## Open Questions

None.

## Out Of Scope

- Multi-tenant SaaS organization/workspace ownership.
- Billing, subscriptions, and usage-based pricing.
- RBAC, admin roles, permissions, and enterprise access control.
- Audit logs and compliance retention policies.
- External document connectors.
- Workflow automation.
- Multi-region scaling.
- Mandatory email verification.
- Keycloak/OIDC login implementation.
- User-selectable AI providers or models in the UI.
- Model-based reranking.
- OCR and image ingestion.
- Spreadsheet, slides, HTML import, ZIP import, and web-page import.
- Full rich PDF/DOCX inline preview, highlighting, and page-level source viewer.
- Antivirus/malware scanning implementation.
- Application-level per-file encryption and key management.
- Full account/workspace export packages.
- Dedicated admin/system-health frontend page.
- General-knowledge fallback in chat by default.
- Public Swagger UI in production by default.
