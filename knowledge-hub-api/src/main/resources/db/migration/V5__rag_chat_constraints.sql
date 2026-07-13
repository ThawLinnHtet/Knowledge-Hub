ALTER TABLE message_citations ADD COLUMN citation_order INTEGER;

UPDATE message_citations
SET citation_order = ordered.position
FROM (
    SELECT id, row_number() OVER (PARTITION BY message_id ORDER BY created_at, id)::INTEGER AS position
    FROM message_citations
) ordered
WHERE message_citations.id = ordered.id;

ALTER TABLE message_citations ALTER COLUMN citation_order SET NOT NULL;
ALTER TABLE message_citations ADD CONSTRAINT ck_message_citations_order
    CHECK (citation_order > 0);
ALTER TABLE message_citations ADD CONSTRAINT uq_message_citations_message_order
    UNIQUE (message_id, citation_order);

WITH duplicates AS (
    SELECT id,
           row_number() OVER (PARTITION BY chat_session_id ORDER BY created_at, id) AS position
    FROM chat_messages
    WHERE role = 'ASSISTANT' AND status = 'PENDING'
)
UPDATE chat_messages
SET status = 'FAILED', updated_at = now()
FROM duplicates
WHERE chat_messages.id = duplicates.id AND duplicates.position > 1;

CREATE UNIQUE INDEX uq_chat_messages_pending_assistant
    ON chat_messages (chat_session_id)
    WHERE role = 'ASSISTANT' AND status = 'PENDING';
