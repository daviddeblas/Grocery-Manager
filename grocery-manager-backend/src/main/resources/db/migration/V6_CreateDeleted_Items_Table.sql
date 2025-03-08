CREATE TABLE deleted_items (
    id BIGSERIAL PRIMARY KEY,
    original_id BIGINT,
    sync_id VARCHAR(100),
    entity_type VARCHAR(50) NOT NULL,
    user_id BIGINT NOT NULL,
    deleted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    synced BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_deleted_items_sync_id ON deleted_items(sync_id);
CREATE INDEX idx_deleted_items_user_id ON deleted_items(user_id);