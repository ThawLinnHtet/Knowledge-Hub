# Knowledge Hub Implementation Ledger

Source: `docs/sdd-knowledge-hub-platform.md`

This ledger is updated after each slice passes its verification gate. A slice is
only `verified` when its targeted tests and the applicable project quality gate
pass.

| Slice | Scope | State | Verification |
| --- | --- | --- | --- |
| 1 | Repository and backend identity foundation | verified | Testcontainers context load and `mvn clean verify` |
| 2 | Database foundation and common backend contracts | verified | Migration, error-contract, context, and formatting tests |
| 3 | Authentication and sessions | verified | Auth API integration suite, protected-route checks, and `mvn clean verify` |
| 4 | Password reset and account deletion lifecycle | verified | Reset/deletion API tests, cleanup retry tests, and `mvn verify` |
| 5 | Storage and document upload preflight | verified | Validation/token/storage tests and `mvn clean verify` |
| 6 | Confirmed upload, collections, library, and downloads | verified | Partial-success upload, collection/library/download API tests, cleanup regression test, and `mvn clean verify` |
| 7 | Ingestion, extraction, chunking, embeddings, and diagnostics | verified | Worker, retry/lease/limit, pgvector persistence, chunking/storage tests, and `mvn verify` |
| 8 | Hybrid search | verified | Keyword/semantic/hybrid API, ownership/status/filter tests, and `mvn verify` |
| 9 | RAG chat and SSE streaming | verified | Session/message CRUD, scope framing, scoped hybrid retrieval, SSE streaming, fake/real provider deltas, cited-label validation, weak-evidence refusal, deleted-source rendering, session-cascade deletion, and `mvn verify` |
| 10 | Frontend foundation and authentication UI | verified | Auth form/session tests, lint, typecheck, format check, and production build |
| 11 | Frontend landing, documents, collections, search, and settings | verified | Public conversion/auth routing, upload/document, collection CRUD, search/filter, settings, cache-isolation, lint, typecheck, format, and production build gates |
| 12 | Frontend chat and citations | verified | Session CRUD, paged scope controls, authenticated SSE framing/refresh/recovery, persisted history, citations, source-deleted rendering, source navigation, lint, typecheck, format, and production build gates |
| 13 | E2E, CI, observability, and final hardening | verified | Health/OpenAPI profile tests, structured-log isolation, CI-equivalent gates, and Playwright critical flow |

## Decisions

- Slice 3 uses manually issued HMAC-SHA256 JWT access tokens. RSA key pairs are
  intentionally not used.
- Refresh tokens remain opaque random values stored only as SHA-256 hashes, as
  required by the SDD.
- Upload preflight returns ordered per-file `ACCEPTED`, `DUPLICATE`, or
  `REJECTED` results so validation failures do not abort a valid batch.
- Upload confirmation tokens are opaque, hashed, short-lived, single-use, and
  bound to the user, hash, filename, size, and optional owned collection.
- Durable cleanup jobs protect object writes and deletions across database or
  storage failures; cleanup cancellation failures do not misreport committed uploads.
- Ingestion uses short PostgreSQL claims with lock-token-fenced finalization,
  bounded Tika extraction, positional overlapping chunks, and deterministic fake
  1024-dimensional embeddings in tests and local fake-AI mode.
- Search exposes explicit keyword, semantic, and hybrid modes; semantic retrieval
  uses bounded HNSW-ordered candidates and hybrid ranking fuses bounded keyword
  and semantic candidates after owner, ready-status, and scope filters.
- The public root route is an evidence-led product overview with account creation
  as the primary action; authenticated visitors bypass it for Documents.

## Current Gate

All 13 implementation slices are verified. The final gate includes
CI-equivalent backend/frontend commands, isolated Playwright core-flow coverage,
observability assertions, and setup documentation.
