/*
 * Stores individual items within shopping lists.
 * Each item belongs to a single shopping list and tracks its status (checked),
 * quantity, and custom sorting order.
 *
 * Features:
 * - Different unit types (kg, L, units)
 * - Tracks checked/unchecked status for shopping progress
 * - Maintains custom sort order through sort_index
 * - Supports cross-device synchronization
 */
CREATE TABLE shopping_items (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,           -- Item name
    quantity DOUBLE PRECISION NOT NULL,   -- Numeric quantity (can be decimal)
    unit_type VARCHAR(50) NOT NULL,       -- Unit of measurement (kg, L, units)
    checked BOOLEAN DEFAULT FALSE,        -- Whether item has been picked/purchased
    sort_index INTEGER DEFAULT 0,         -- Custom order position in the list
    shopping_list_id BIGINT NOT NULL,     -- Shopping list identifier
    sync_id VARCHAR(100) UNIQUE,          -- UUID for synchronization
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_synced TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,             -- For optimistic locking
    FOREIGN KEY (shopping_list_id) REFERENCES shopping_lists(id) ON DELETE CASCADE
);

-- Indexes for performance and data integrity
CREATE INDEX idx_shopping_items_list_id ON shopping_items(shopping_list_id);
CREATE INDEX idx_shopping_items_sync_id ON shopping_items(sync_id);