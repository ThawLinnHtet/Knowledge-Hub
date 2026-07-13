## Workflow

Default workflow:

1. Grill unclear ideas before planning.
2. Convert approved ideas into PRD.
3. Convert PRD into SDD before implementation.
4. Use TDD for features and bug fixes.
5. Run code review before merge.
6. Use frontend UI design/review skills for UI work.
7. Use architecture review only when the codebase is messy or risky.

## Engineering Rules

- Inspect the codebase before editing.
- Prefer the smallest correct change.
- Do not rewrite large areas unless necessary.
- Run tests/build/lint before final response.
- Never revert user changes without permission.

## Git Rules

- One branch per task.
- Commit only after verification.
- Use git worktree only for parallel tasks.

## Technology Stack

### Backend

- Java 25
- Spring Boot
- Spring AI
- Spring Security
- Spring Data JPA
- Flyway
- PostgreSQL
- pgvector
- MinIO
- MapStruct

### Frontend

- React 19
- TypeScript
- Vite
- Tailwind css
- TanStack Query
- Zustand
- React Router
- Axios
- shadcn/ui

### Infrastructure

- Docker Compose
- PostgreSQL
- MinIO
