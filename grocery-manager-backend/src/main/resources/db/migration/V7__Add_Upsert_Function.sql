/*
 * Provides an atomic "upsert" (insert or update) operation for shopping items
 * to handle concurrent modifications from multiple devices.
 *
 * This function:
 * 1. Attempts to insert a new item with the given sync_id
 * 2. If that sync_id already exists, updates the existing item instead
 * 3. Returns the ID of the inserted or updated item
 *
 * Benefits:
 * - Prevents race conditions during synchronization
 * - Avoids the need for separate find-then-update/insert logic
 * - Maintains data integrity during concurrent operations
 */
CREATE OR REPLACE FUNCTION safe_upsert_shopping_item(
    p_name VARCHAR(255),              -- Item name
    p_quantity DOUBLE PRECISION,      -- Quantity value
    p_unit_type VARCHAR(50),          -- Unit type (kg, L, units)
    p_checked BOOLEAN,                -- Whether item is checked
    p_sort_index INTEGER,             -- Display order
    p_shopping_list_id BIGINT,        -- Parent list ID
    p_sync_id VARCHAR(100),           -- Synchronization ID
    p_created_at TIMESTAMP,           -- Creation timestamp
    p_updated_at TIMESTAMP,           -- Last update timestamp
    p_last_synced TIMESTAMP           -- Last sync timestamp
) RETURNS BIGINT AS $$
DECLARE
    v_item_id BIGINT;
BEGIN
    -- Try to insert first with ON CONFLICT DO UPDATE
    INSERT INTO shopping_items (
        name, quantity, unit_type, checked, sort_index,
        shopping_list_id, sync_id, created_at, updated_at, last_synced, version
    )
    VALUES (
               p_name, p_quantity, p_unit_type, p_checked, p_sort_index,
               p_shopping_list_id, p_sync_id, p_created_at, p_updated_at, p_last_synced, 0
           )
    ON CONFLICT (sync_id)
        DO UPDATE SET
          name = p_name,
          quantity = p_quantity,
          unit_type = p_unit_type,
          checked = p_checked,
          sort_index = p_sort_index,
          updated_at = p_updated_at,
          last_synced = p_last_synced
    RETURNING id INTO v_item_id;

    RETURN v_item_id;
END;
$$ LANGUAGE plpgsql;