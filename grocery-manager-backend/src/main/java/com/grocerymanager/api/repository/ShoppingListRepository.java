package com.grocerymanager.api.repository;

import com.grocerymanager.api.model.ShoppingList;
import com.grocerymanager.api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShoppingListRepository extends JpaRepository<ShoppingList, Long> {
    List<ShoppingList> findAllByUser(User user);

    Optional<ShoppingList> findByIdAndUser(Long id, User user);

    Optional<ShoppingList> findBySyncIdAndUser(String syncId, User user);

    List<ShoppingList> findByUserAndLastSyncedAfter(User user, LocalDateTime lastSynced);
}