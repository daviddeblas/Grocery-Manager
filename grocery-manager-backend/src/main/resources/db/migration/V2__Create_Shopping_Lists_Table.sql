/*
 * Stores shopping lists created by users.
 * Each list belongs to one user and can contain multiple shopping items.
 *
 * Synchronization features:
 * - sync_id: Unique identifier for cross-device synchronization
 * - last_synced: Timestamp for conflict resolution
 * - version: For optimistic locking during concurrent modifications
 */
CREATE TABLE shopping_lists (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,           -- Display name of the shopping list
    user_id BIGINT NOT NULL,              -- Owner of the list
    sync_id VARCHAR(100) UNIQUE,          -- UUID for synchronization between devices
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_synced TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- Last synchronization time
    version BIGINT DEFAULT 0,             -- Optimistic locking version
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Indexes for quick retrieval and synchronization
CREATE INDEX idx_shopping_lists_user_id ON shopping_lists(user_id);
CREATE INDEX idx_shopping_lists_sync_id ON shopping_lists(sync_id);