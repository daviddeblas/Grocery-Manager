-- Function to safely insert or update a shopping item based on sync_id
CREATE OR REPLACE FUNCTION safe_upsert_shopping_item(
    p_name VARCHAR(255),
    p_quantity DOUBLE PRECISION,
    p_unit_type VARCHAR(50),
    p_checked BOOLEAN,
    p_sort_index INTEGER,
    p_shopping_list_id BIGINT,
    p_sync_id VARCHAR(100),
    p_created_at TIMESTAMP,
    p_updated_at TIMESTAMP,
    p_last_synced TIMESTAMP
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