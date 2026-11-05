CREATE TABLE IF NOT EXISTS ai_pet_record (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 pet_id BIGINT NOT NULL,
 kind VARCHAR(16) NOT NULL,
 content VARCHAR(1000) NOT NULL,
 occurred_on DATE NULL,
 event_state VARCHAR(16) NULL,
 source_message_id VARCHAR(64) NULL,
 status TINYINT NOT NULL DEFAULT 1,
 created_at DATETIME NOT NULL,
 updated_at DATETIME NOT NULL,
 UNIQUE KEY uk_pet_source(user_id,pet_id,source_message_id),
 KEY idx_pet_records(user_id,pet_id,status,kind,updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
