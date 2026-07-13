ALTER TABLE collections
    ADD CONSTRAINT uq_collections_id_user UNIQUE (id, user_id);

ALTER TABLE documents
    DROP CONSTRAINT documents_collection_id_fkey,
    ADD CONSTRAINT fk_documents_collection_owner
        FOREIGN KEY (collection_id, user_id) REFERENCES collections (id, user_id);

ALTER TABLE upload_confirmation_tokens
    DROP CONSTRAINT upload_confirmation_tokens_collection_id_fkey,
    ADD CONSTRAINT fk_upload_confirmation_tokens_collection_owner
        FOREIGN KEY (collection_id, user_id) REFERENCES collections (id, user_id) ON DELETE CASCADE;

CREATE INDEX ix_upload_confirmation_tokens_expires_at
    ON upload_confirmation_tokens (expires_at);

CREATE INDEX ix_upload_confirmation_tokens_used_at
    ON upload_confirmation_tokens (used_at) WHERE used_at IS NOT NULL;
