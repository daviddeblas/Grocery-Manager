package com.grocerymanager.api.repository;

import com.grocerymanager.api.model.ShoppingItem;
import com.grocerymanager.api.model.ShoppingList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShoppingItemRepository extends JpaRepository<ShoppingItem, Long> {
    List<ShoppingItem> findAllByShoppingList(ShoppingList shoppingList);

    List<ShoppingItem> findAllByShoppingListOrderBySortIndexAsc(ShoppingList shoppingList);

    @Query("SELECT i FROM ShoppingItem i WHERE i.shoppingList = :list ORDER BY i.checked ASC, i.name ASC")
    List<ShoppingItem> findAllByShoppingListOrderByCheckedAndName(@Param("list") ShoppingList shoppingList);

    Optional<ShoppingItem> findBySyncId(String syncId);

    List<ShoppingItem> findByShoppingListAndLastSyncedAfter(ShoppingList shoppingList, LocalDateTime lastSynced);
}