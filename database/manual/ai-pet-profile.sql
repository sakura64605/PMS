CREATE TABLE IF NOT EXISTS ai_pet_profile (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 name VARCHAR(40) NOT NULL,
 species VARCHAR(16) NOT NULL,
 breed VARCHAR(60) NOT NULL DEFAULT '',
 birth_date DATE NULL,
 sex VARCHAR(16) NOT NULL DEFAULT 'unknown',
 updated_at DATETIME NOT NULL,
 INDEX idx_pet_owner(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Check information_schema.columns before applying this once.
ALTER TABLE ai_chat_session ADD COLUMN pet_id BIGINT NULL;
