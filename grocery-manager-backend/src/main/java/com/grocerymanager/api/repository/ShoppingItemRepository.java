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

@Repository
public interface ShoppingItemRepository extends JpaRepository<ShoppingItem, Long> {
    List<ShoppingItem> findAllByShoppingList(ShoppingList shoppingList);

    List<ShoppingItem> findAllByShoppingListOrderBySortIndexAsc(ShoppingList shoppingList);

    @Query("SELECT i FROM ShoppingItem i WHERE i.shoppingList = :list ORDER BY i.checked ASC, i.name ASC")
    List<ShoppingItem> findAllByShoppingListOrderByCheckedAndName(@Param("list") ShoppingList shoppingList);

    // Find by syncId with pessimistic lock to prevent race conditions
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ShoppingItem> findBySyncId(String syncId);

    // Check if an item exists by syncId
    boolean existsBySyncId(String syncId);

    // Update an existing item directly by syncId to avoid race conditions
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

    List<ShoppingItem> findByShoppingListAndLastSyncedAfter(ShoppingList shoppingList, LocalDateTime lastSynced);

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