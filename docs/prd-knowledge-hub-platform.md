# PRD: Knowledge Hub Platform

## Problem Statement

Individuals and small teams accumulate important knowledge across PDFs, DOCX files, text files, and Markdown documents, but that knowledge is difficult to organize, search, verify, and reuse. Traditional document search often fails when users ask natural-language questions, while generic LLM chat systems answer from pre-trained knowledge rather than from the user's own documents.

Knowledge Hub should let users transform private document collections into an intelligent, searchable knowledge base. Users need to upload documents, organize them, search semantically and by keyword, and chat with their documents using Retrieval-Augmented Generation. Answers must be grounded in retrieved source material, cite the documents and chunks used, and avoid unsupported hallucinated responses.

The platform should be production-quality from the beginning without becoming a full enterprise SaaS product. It should prioritize clean architecture, security, reliable ingestion, maintainability, testability, and future extensibility.

## Solution

Build Knowledge Hub as a monorepo containing a Spring Boot API backend and a React/Vite frontend. The first platform version is a single-tenant personal/team knowledge platform where each authenticated user has a private knowledge base. Multi-tenant SaaS capabilities are future improvements.

Users can register and log in with internal email/password authentication. They can upload multiple supported document files, organize documents into collections, monitor ingestion status, search across ready documents, and create saved RAG chat sessions. Uploaded originals are stored privately in MinIO. Extracted chunks, metadata, embeddings, search fields, chats, citations, sessions, and account data are stored in PostgreSQL with pgvector.

Document ingestion is asynchronous and database-backed. Upload creates durable document records and private object storage entries. A Spring in-process worker claims pending documents from PostgreSQL, extracts text, chunks content, generates embeddings, populates hybrid search data, and records diagnostics or failure metadata.

RAG chat uses scoped hybrid retrieval over the user's ready chunks, sends grounded context to the configured chat provider, streams responses to the frontend with SSE, and persists final assistant messages and structured citations. The assistant must answer from uploaded documents by default, cite sources at paragraph/answer-section level, and refuse or warn when retrieved evidence is weak.

## User Stories

1. As a new user, I want to register with email and password, so that I can create my private Knowledge Hub account.
2. As a returning user, I want to log in securely, so that I can access my documents and chat history.
3. As an authenticated user, I want short-lived access tokens and refresh-token sessions, so that I can stay signed in without exposing long-lived credentials to JavaScript.
4. As a user who forgot my password, I want to request a reset link, so that I can recover my account.
5. As a user, I want authentication endpoints protected from brute-force attempts, so that my account is safer.
6. As a user, I want open self-registration when enabled, so that local/demo use is simple.
7. As a deployer, I want registration to be configurable, so that a production instance can prevent unknown signups.
8. As a user, I want to upload PDF, DOCX, TXT, and Markdown files, so that common knowledge documents can be indexed.
9. As a user, I want to upload multiple files in one action, so that I can build my knowledge base quickly.
10. As a user, I want file validation to reject unsupported or suspicious files, so that the platform does not process unsafe content.
11. As a user, I want duplicate-file detection before ingestion, so that I can avoid clutter and unnecessary model costs.
12. As a user, I want to explicitly confirm duplicate uploads, so that duplicates are only created intentionally.
13. As a user, I want documents stored privately, so that my originals are not publicly accessible.
14. As a user, I want original file downloads, so that I can retrieve the source file later.
15. As a user, I want documents organized into collections, so that I can group related knowledge.
16. As a user, I want an Uncategorized collection fallback, so that every document has a place even if I do not organize it manually.
17. As a user, I want deleting a collection to move documents to Uncategorized, so that I do not lose documents accidentally.
18. As a user, I want to see document processing status, so that I know whether documents are searchable.
19. As a user, I want concise processing diagnostics, so that I can understand failed or weak ingestions.
20. As a user, I want failed ingestion to retry when failures are transient, so that temporary provider/storage problems recover automatically.
21. As a user, I want to manually retry failed documents, so that I can recover after configuration or temporary issues.
22. As a user, I want permanent document deletion, so that I can remove source files and extracted data completely.
23. As a user, I want deleted document citations to show as source deleted, so that old chats remain understandable without exposing removed content.
24. As a user, I want a basic document detail view, so that I can inspect metadata, diagnostics, chunks/snippets, citations, and download the original.
25. As a user, I want global quick search, so that I can find relevant content from anywhere in the dashboard.
26. As a user, I want a full search page with filters, so that I can perform focused searches across documents.
27. As a user, I want search to combine semantic and keyword matching, so that both meaning and exact terms are found.
28. As a user, I want search filters for collection, selected documents, type, and upload date, so that I can narrow results.
29. As a user, I want chat to default to all ready documents, so that I can ask questions immediately.
30. As a user, I want chat scope filters by collection or documents, so that I can ask focused questions.
31. As a user, I want chat sessions saved, renamed, revisited, and deleted, so that document conversations are durable and manageable.
32. As a user, I want each chat session to remember its scope, so that retrieval behavior is understandable later.
33. As a user, I want to change chat scope before sending a message, so that I can refine context during a conversation.
34. As a user, I want multi-turn chat context, so that follow-up questions work naturally.
35. As a user, I want each turn to retrieve fresh document context, so that answers stay grounded in current sources.
36. As a user, I want streaming chat responses, so that long RAG answers feel responsive.
37. As a user, I want citations linked to specific chunks, pages, sections, or positions, so that answers are auditable.
38. As a user, I want answer citations at paragraph or answer-section level, so that the response is readable but source-backed.
39. As a user, I want the assistant to say when evidence is weak or absent, so that I do not mistake guesses for grounded answers.
40. As a user, I want the assistant to ignore malicious instructions embedded in documents, so that uploaded content cannot override system behavior.
41. As a user, I want chat deletion to remove messages and citations, so that I control my chat data.
42. As a user, I want account deletion to remove my documents, chunks, embeddings, chats, citations, sessions, tokens, and files, so that I can permanently leave the platform.
43. As a user, I want account deletion to require strong confirmation, so that I do not destroy data accidentally.
44. As a user, I want account deletion cleanup to be reliable, so that storage and database data are eventually removed even if cleanup has transient failures.
45. As a user, I want clear error messages for validation, auth, limits, upload, ingestion, provider, and storage failures, so that I know what went wrong.
46. As a user, I want backend cost limits to stop oversized documents and expensive requests, so that model costs stay controlled.
47. As a user, I want settings to show non-secret system/model configuration, so that I know what platform capabilities and limits are active.
48. As a frontend user, I want a dashboard shell with persistent navigation, so that Documents, Collections, Search, Chat, and Settings are always accessible.
49. As a developer, I want deterministic fake AI mode, so that tests and local development work without paid API keys.
50. As a developer, I want generated API documentation in local/dev, so that frontend/backend contracts are clear.
51. As a deployer, I want health and readiness endpoints, so that the platform can be monitored.
52. As a developer, I want structured logs without sensitive payloads, so that production issues can be diagnosed safely.
53. As a prospective user, I want a public overview of Knowledge Hub with a clear account-creation action, so that I can understand its private, source-backed workflow before registering.

## Implementation Decisions

- Product scope is a production-quality personal/team platform first, not a throwaway MVP and not a full enterprise SaaS product.
- Multi-tenant SaaS, billing, RBAC, audit logs, connectors, workflow automation, and multi-region scaling are future improvements.
- Each authenticated user has a private knowledge base. Shared team workspaces and memberships are future improvements.
- Internal email/password authentication is initial scope. Keycloak/OIDC support is a future authentication enhancement.
- Authentication uses Spring Security with JWT access tokens and refresh tokens.
- Refresh tokens are stored as hashed records in PostgreSQL with expiry, revocation timestamp, session/device metadata, and rotation on use.
- Refresh tokens are delivered through HttpOnly secure cookies. Access tokens are returned to the frontend and held outside durable browser storage where feasible.
- CSRF protection applies to cookie-backed state-changing auth endpoints, while bearer-token API requests stay stateless.
- Passwords are strongly hashed.
- Password reset is in scope. Reset tokens are short-lived, single-use, stored hashed in PostgreSQL, and delivered by SMTP in production.
- SMTP is optional for local development; when disabled, reset links/tokens may be logged for dev only.
- Mandatory email verification is out of initial scope.
- Self-registration is open when enabled and configurable through environment settings.
- The public root route explains the document-to-evidence workflow and prioritizes account creation, with sign-in as the secondary action. Authenticated visitors bypass it and enter their document library.
- Authentication endpoints include in-process rate limiting with conservative per-IP and per-account cooldowns.
- Supported initial document types are PDF, DOCX, TXT, and Markdown.
- Uploaded originals are stored in private MinIO buckets. Extracted chunks, metadata, embeddings, and statuses are stored in PostgreSQL.
- Original files are accessed through backend-authorized, short-lived MinIO pre-signed URLs.
- MinIO object keys are opaque generated keys. Original filenames are stored only as metadata.
- Infrastructure-level encryption is relied on initially; application-level file encryption is future hardening.
- Uploaded file validation checks extension, detected content type, and size. Browser-provided MIME type is not trusted.
- Antivirus scanning is deferred, but upload/ingestion design should leave a clear scanning hook.
- Duplicate detection uses SHA-256 hashes per user.
- Duplicate handling uses a two-step confirmation flow with short-lived server-issued confirmation tokens stored hashed with expiry and used-at timestamp.
- Multiple files can be uploaded in one action, with per-file status and errors.
- Document ingestion is asynchronous.
- The ingestion worker is an in-process Spring worker backed by durable PostgreSQL state.
- The ingestion worker polls and claims eligible documents from the database.
- Claiming uses lock/lease fields to avoid duplicate processing and prepare for future multi-instance workers.
- User-facing document status includes clear states such as pending, processing, ready, and failed.
- Failed status uses internal failure metadata including failure code, message, retry count, next retry time, and retryability.
- Transient ingestion failures are retried automatically with a capped retry count.
- Manual retry is available for failed documents.
- Apache Tika is the primary extraction layer behind a text-extraction interface.
- PDF page numbers are extracted when feasible, with fallback to chunk positions.
- Chunks, not giant full-document text blobs, are the primary extracted text storage unit.
- Chunks store text, order, page/section metadata, character counts, token estimates where available, full-text search data, embedding metadata, and vector embeddings.
- Chunking is structure-aware where possible. Markdown and DOCX should preserve headings/paragraph boundaries. PDF and TXT can use configurable character-based chunking with overlap.
- Prompt assembly is token-budget aware even if chunking starts with character-based sizing.
- Embeddings use a custom document chunks table with a pgvector embedding column, not only a generic vector-store schema.
- One embedding dimension is active per deployment. Embedding model and dimension metadata are stored, but multiple simultaneous searchable dimensions are not initial scope.
- Vector similarity uses cosine distance by default.
- pgvector indexing uses HNSW with cosine operations by default.
- Chat provider starts with OpenRouter or another OpenAI-compatible provider configured through backend settings.
- Embedding provider is configurable separately behind backend settings if OpenRouter embedding support is unavailable or unsuitable. If OpenRouter supports the required embeddings affordably, it may be used.
- AI provider/model settings are not user-selectable in the initial UI.
- Cost controls are enforced as hard backend limits for upload size, extracted character count, chunk count, retrieved chunk count, and chat/prompt token budget.
- Search is hybrid from the beginning, combining semantic vector search and PostgreSQL full-text keyword search.
- Hybrid retrieval powers both user-facing search and RAG retrieval.
- Model-based reranking is deferred, but retrieval pipeline design should allow a reranking step later.
- Search filters include collection scope, selected documents, document type, and upload date.
- RAG retrieval always enforces user ownership, ready processing status, and selected chat scope.
- Chat sessions are top-level product objects with saved history.
- Chat scope belongs to the session and can be changed before sending a message. The scope used should be persisted per message for explainability.
- Chat preserves recent conversation context while retrieving fresh document evidence per turn.
- Chat response streaming uses Server-Sent Events.
- RESTful resources are used for documents, collections, search, and chat history. Streaming chat message send uses a command-style SSE endpoint.
- APIs are versioned from the beginning.
- API responses use a standard error contract with machine-readable codes and human-readable messages.
- Backend uses separate DTO/request/response models rather than exposing persistence entities.
- MapStruct is used for straightforward DTO/entity mapping. Services should orchestrate business rules and call mappers rather than manually mapping DTOs.
- Feature-module package organization is preferred, with internal layers per feature.
- Backend project identity should use production-style Knowledge Hub naming rather than scaffold defaults.
- Repository structure is a monorepo with separate backend and frontend apps.
- Local development uses root-level Docker Compose for infrastructure services such as PostgreSQL/pgvector and MinIO. Backend and frontend run separately for fast feedback.
- Production deployment should use separate containers for frontend, backend, PostgreSQL, and MinIO.
- Frontend uses React, TypeScript, Vite, Tailwind CSS, shadcn/ui, TanStack Query, Zustand, React Router, and Axios.
- TanStack Query is the primary server-state layer. Zustand is reserved for local UI/session preferences and should not duplicate backend data.
- The frontend uses a dashboard shell for authenticated app pages and standalone layouts for auth pages.
- Navigation includes Documents, Collections, Search, Chat, and Settings.
- Search is available as both global quick search and a full search page.
- Document statuses and ingestion diagnostics live inside the Documents area rather than a separate processing queue page.
- Settings includes account/session controls plus read-only non-secret system/model/limit information.
- No dedicated admin/system-health UI is initially included because no admin/RBAC model exists.
- OpenAPI/Swagger documentation is enabled for local/dev. Production exposure is disabled or explicitly protected/configurable.
- Spring Boot Actuator provides health/readiness/liveness and safe basic metrics.
- Structured JSON logging uses request/correlation IDs and safe metadata only.
- Sensitive AI payload logging is disabled by default. Development-only unsafe payload logging may be added behind an explicit flag.
- Chunks, embeddings, prompts, user questions, and document text are treated as sensitive data and must not be logged by default.
- CORS uses configured allowed origins, not wildcards.
- Secrets and environment-specific configuration are supplied through environment variables and profiles.
- Safe example environment files and setup documentation should be committed. Real secrets are ignored.
- Manual Flyway SQL migrations own database schema creation and evolution.
- Hibernate validates schema in non-test environments rather than generating or mutating schema.
- UUIDs are used for user-facing/domain entity primary keys.
- PostgreSQL `gen_random_uuid()` is the default ID source, with application-generated UUIDs only when needed before persistence.
- Major domain tables include standard created/updated timestamps plus lifecycle-specific timestamps only where meaningful.
- User-triggered document and chat deletion are hard deletes.
- Account deletion marks the account deletion-pending, revokes sessions/tokens immediately, then runs cleanup in a background job.
- Account deletion starts irreversible cleanup immediately after explicit confirmation with password re-entry and typed confirmation.
- Chat citations are stored as structured records linked to assistant messages.
- Citations store document ID, chunk ID, title/filename snapshot, page/section/chunk position, relevance score, and source-deleted state.
- Chat messages do not store full retrieved chunk text snapshots.
- The assistant strictly answers from uploaded documents by default and refuses or warns when evidence is insufficient.
- Retrieved document chunks are treated as untrusted content, not instructions.

## Testing Decisions

- Use TDD for feature and bug-fix implementation after the PRD and SDD are complete.
- Good tests should verify externally observable behavior rather than implementation details.
- Backend tests should prioritize integration tests with Testcontainers for PostgreSQL/pgvector behavior, Flyway migrations, constraints, repository/service/API behavior, auth, and ingestion persistence.
- Backend unit tests should cover isolated logic such as validation, chunking, token budgeting, cost-limit enforcement, deterministic fake AI behavior, and mapper behavior.
- Automated tests should not call real paid AI providers by default.
- AI provider integration is behind interfaces so tests can use deterministic fake chat and embedding clients.
- Optional real-provider smoke tests may exist under an explicit manual/profile-based mode.
- Local and test profiles include deterministic fake AI mode with fake embeddings and predictable cited chat responses.
- Frontend tests use Vitest and React Testing Library for auth forms, upload states, search filters/results, chat streaming state handling, settings/status rendering, and error rendering.
- E2E tests use a small Playwright suite for critical happy paths: register/login, upload documents, wait for processing, search, chat with citations, and delete a document.
- E2E tests should stay few and stable. Edge cases belong mostly in backend/frontend integration tests.
- CI runs backend tests, frontend lint/typecheck/tests, and eventually Playwright smoke tests using fake AI mode.
- CI is a required quality gate before merging.
- Backend formatting/static analysis is Maven-enforced with practical defaults.
- Frontend uses TypeScript strict mode, ESLint, and Prettier, enforced in CI.

## Out of Scope

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

## Further Notes

- The existing repository currently contains a minimal Spring Boot scaffold with Spring AI, JPA, Flyway, OAuth2 resource server, Testcontainers, and pgvector Docker Compose support. A React frontend does not yet exist.
- This PRD intentionally captures product and implementation decisions without starting implementation.
- The next step is to convert this PRD into an SDD with implementation slices, database design, API contracts, frontend route/component plans, and test gates.
- The issue-tracker publication step was not completed because no issue-tracker workflow or label configuration was present in the repository context. This PRD is stored as the source artifact for later publication if an issue tracker is configured.
