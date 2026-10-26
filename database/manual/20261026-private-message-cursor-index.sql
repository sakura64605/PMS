-- Apply once before deploying cursor-based private message history.
-- This complements (conversation_id, create_time); it does not replace it.
CREATE INDEX idx_private_message_conversation_id ON private_message (conversation_id, id);
