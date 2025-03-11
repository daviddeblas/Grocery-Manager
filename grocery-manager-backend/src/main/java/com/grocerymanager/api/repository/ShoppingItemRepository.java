package com.grocerymanager.api.repository;

import com.grocerymanager.api.model.ShoppingItem;
import com.grocerymanager.api.model.ShoppingList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for managing shopping items in the database.
 * - Provides methods to retrieve, sort, update, and synchronize items.
 * - Uses JPQL and native queries for efficient database interactions.
 * - Implements locking mechanisms to prevent race conditions.
 */
@Repository
public interface ShoppingItemRepository extends JpaRepository<ShoppingItem, Long> {

    /**
     * **Find all items for a given shopping list.**
     */
    List<ShoppingItem> findAllByShoppingList(ShoppingList shoppingList);

    /**
     * **Retrieve all items from a shopping list, ordered by their display order.**
     */
    List<ShoppingItem> findAllByShoppingListOrderBySortIndexAsc(ShoppingList shoppingList);

    /**
     * Find all items in a shopping list, sorted by:
     * - Checked status first (unchecked items first).
     * - Alphabetical order by name.
     */
    @Query("SELECT i FROM ShoppingItem i WHERE i.shoppingList = :list ORDER BY i.checked ASC, i.name ASC")
    List<ShoppingItem> findAllByShoppingListOrderByCheckedAndName(@Param("list") ShoppingList shoppingList);

    /**
     * Find an item by its syncId with a lock.
     * - Prevent concurrent modifications thanks to pessimistic write lock.
     * - Ensures that only one transaction can update the item at a time.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ShoppingItem> findBySyncId(String syncId);


    /**
     * Update an item directly using its syncId to avoid race conditions.
     * - Uses a modifying query to update only the specified fields.
     * - Ensures that the item's last update timestamp and sync timestamp are recorded.
     */
    @Modifying
    @Query("UPDATE ShoppingItem i SET i.name = :name, i.quantity = :quantity, " +
            "i.unitType = :unitType, i.checked = :checked, i.sortIndex = :sortIndex, " +
            "i.updatedAt = :updatedAt, i.lastSynced = :lastSynced " +
            "WHERE i.syncId = :syncId")
    int updateBySyncId(@Param("syncId") String syncId,
                       @Param("name") String name,
                       @Param("quantity") Double quantity,
                       @Param("unitType") String unitType,
                       @Param("checked") boolean checked,
                       @Param("sortIndex") int sortIndex,
                       @Param("updatedAt") LocalDateTime updatedAt,
                       @Param("lastSynced") LocalDateTime lastSynced);

    /**
     * Find all items modified after a specific synchronization timestamp.
     * - Used to retrieve changes for incremental synchronization.
     */
    List<ShoppingItem> findByShoppingListAndLastSyncedAfter(ShoppingList shoppingList, LocalDateTime lastSynced);

    /**
     * Update or Insert (UPSERT) a shopping item efficiently.
     * - If an item with the same syncId exists, update its values.
     * - If not, insert a new item into the database.
     * - Atomicity with: `ON CONFLICT (sync_id) DO UPDATE` .
     */
    @Query(value = "WITH upserted AS (" +
            "    INSERT INTO shopping_items(name, quantity, unit_type, checked, sort_index, " +
            "                           shopping_list_id, sync_id, created_at, updated_at, last_synced, version) " +
            "    VALUES (:name, :quantity, :unitType, :checked, :sortIndex, " +
            "            :shoppingListId, :syncId, :createdAt, :updatedAt, :lastSynced, 0) " +
            "    ON CONFLICT (sync_id) DO UPDATE SET " +
            "        name = :name, " +
            "        quantity = :quantity, " +
            "        unit_type = :unitType, " +
            "        checked = :checked, " +
            "        sort_index = :sortIndex, " +
            "        updated_at = :updatedAt, " +
            "        last_synced = :lastSynced " +
            "    RETURNING id" +
            ") " +
            "SELECT id FROM upserted", nativeQuery = true)
    Long safeUpsertItem(@Param("name") String name,
                        @Param("quantity") Double quantity,
                        @Param("unitType") String unitType,
                        @Param("checked") Boolean checked,
                        @Param("sortIndex") Integer sortIndex,
                        @Param("shoppingListId") Long shoppingListId,
                        @Param("syncId") String syncId,
                        @Param("createdAt") LocalDateTime createdAt,
                        @Param("updatedAt") LocalDateTime updatedAt,
                        @Param("lastSynced") LocalDateTime lastSynced);
}
