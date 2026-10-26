-- Inspect existing columns/indexes before applying; old messages keep NULL keys.
ALTER TABLE private_message
    ADD COLUMN client_message_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD UNIQUE INDEX uk_private_message_sender_client (from_user_id, client_message_id),
    ALGORITHM=INPLACE, LOCK=NONE;
