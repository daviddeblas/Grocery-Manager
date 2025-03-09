/*
 * Tracks items that have been deleted on one device to ensure
 * they are also deleted during synchronization with other devices.
 *
 * - When an item is deleted locally, its ID is recorded here
 * - During sync, these IDs are sent to the server
 * - The server deletes matching items and broadcasts the deletion
 * - Other devices remove these items during their next sync
 */
CREATE TABLE deleted_items (
   id BIGSERIAL PRIMARY KEY,
   original_id BIGINT,                   -- ID of the deleted entity
   sync_id VARCHAR(100),                 -- UUID of the deleted entity
   entity_type VARCHAR(50) NOT NULL,     -- Type of deleted entity (list, item, store)
   user_id BIGINT NOT NULL,              -- Owner of the deleted entity
   deleted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- When deletion occurred
   synced BOOLEAN DEFAULT FALSE,         -- Whether deletion has been synced
   FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_deleted_items_sync_id ON deleted_items(sync_id);
CREATE INDEX idx_deleted_items_user_id ON deleted_items(user_id);