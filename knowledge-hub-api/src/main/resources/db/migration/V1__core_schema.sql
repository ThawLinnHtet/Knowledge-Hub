CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'DELETION_PENDING')),
    deletion_requested_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_users_email_normalized ON users (lower(email));

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    rotated_to_token_id UUID REFERENCES refresh_tokens (id) ON DELETE SET NULL,
    session_name VARCHAR(255),
    device_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    last_used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_expires_at ON refresh_tokens (expires_at);

CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    requested_ip VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_password_reset_tokens_user_id ON password_reset_tokens (user_id);

CREATE TABLE collections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name VARCHAR(160) NOT NULL,
    uncategorized BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_collections_user_name ON collections (user_id, lower(name));
CREATE UNIQUE INDEX uq_collections_user_uncategorized
    ON collections (user_id) WHERE uncategorized;

CREATE TABLE documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    collection_id UUID NOT NULL REFERENCES collections (id),
    original_filename VARCHAR(512) NOT NULL,
    object_key VARCHAR(512) NOT NULL UNIQUE,
    media_type VARCHAR(255) NOT NULL,
    file_extension VARCHAR(32) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes >= 0),
    sha256_hash VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED')),
    failure_code VARCHAR(64),
    failure_message VARCHAR(1000),
    retry_count INTEGER NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
    retryable BOOLEAN NOT NULL DEFAULT false,
    next_retry_at TIMESTAMPTZ,
    processing_lock_id UUID,
    processing_lock_expires_at TIMESTAMPTZ,
    processing_started_at TIMESTAMPTZ,
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_documents_user_created_at ON documents (user_id, created_at DESC);
CREATE INDEX ix_documents_user_hash ON documents (user_id, sha256_hash);
CREATE INDEX ix_documents_collection_id ON documents (collection_id);
CREATE INDEX ix_documents_claim ON documents (status, next_retry_at, processing_lock_expires_at);

CREATE TABLE upload_confirmation_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    collection_id UUID REFERENCES collections (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    file_hash VARCHAR(64) NOT NULL,
    filename VARCHAR(512) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes >= 0),
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_upload_confirmation_tokens_user_id
    ON upload_confirmation_tokens (user_id);

CREATE TABLE document_chunks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    chunk_order INTEGER NOT NULL CHECK (chunk_order >= 0),
    content TEXT NOT NULL,
    page_number INTEGER CHECK (page_number > 0),
    section VARCHAR(512),
    start_position INTEGER CHECK (start_position >= 0),
    end_position INTEGER CHECK (end_position >= 0),
    character_count INTEGER NOT NULL CHECK (character_count >= 0),
    token_estimate INTEGER CHECK (token_estimate >= 0),
    embedding_model VARCHAR(255) NOT NULL,
    embedding_dimension INTEGER NOT NULL CHECK (embedding_dimension = 1024),
    embedding vector(1024) NOT NULL,
    search_vector tsvector GENERATED ALWAYS AS (
        to_tsvector('english', coalesce(section, '') || ' ' || content)
    ) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (document_id, chunk_order)
);

CREATE INDEX ix_document_chunks_document_id ON document_chunks (document_id);
CREATE INDEX ix_document_chunks_search_vector ON document_chunks USING gin (search_vector);
CREATE INDEX ix_document_chunks_embedding_hnsw
    ON document_chunks USING hnsw (embedding vector_cosine_ops);

CREATE TABLE chat_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    scope_type VARCHAR(32) NOT NULL DEFAULT 'ALL'
        CHECK (scope_type IN ('ALL', 'COLLECTION', 'DOCUMENTS')),
    collection_id UUID REFERENCES collections (id) ON DELETE SET NULL,
    document_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_chat_sessions_user_updated_at ON chat_sessions (user_id, updated_at DESC);

CREATE TABLE chat_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_session_id UUID NOT NULL REFERENCES chat_sessions (id) ON DELETE CASCADE,
    role VARCHAR(16) NOT NULL CHECK (role IN ('USER', 'ASSISTANT')),
    content TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'COMPLETE'
        CHECK (status IN ('PENDING', 'COMPLETE', 'FAILED')),
    scope_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_chat_messages_session_created_at
    ON chat_messages (chat_session_id, created_at);

CREATE TABLE message_citations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id UUID NOT NULL REFERENCES chat_messages (id) ON DELETE CASCADE,
    document_id UUID REFERENCES documents (id) ON DELETE SET NULL,
    chunk_id UUID REFERENCES document_chunks (id) ON DELETE SET NULL,
    source_title VARCHAR(512) NOT NULL,
    page_number INTEGER,
    section VARCHAR(512),
    chunk_position INTEGER,
    relevance_score DOUBLE PRECISION NOT NULL,
    source_deleted BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_message_citations_message_id ON message_citations (message_id);
CREATE INDEX ix_message_citations_document_id ON message_citations (document_id);

CREATE TABLE account_deletion_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    retry_count INTEGER NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
    next_retry_at TIMESTAMPTZ,
    failure_message VARCHAR(1000),
    locked_at TIMESTAMPTZ,
    lock_expires_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_account_deletion_jobs_claim
    ON account_deletion_jobs (status, next_retry_at, lock_expires_at);
