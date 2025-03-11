package com.grocerymanager.api.repository;

import com.grocerymanager.api.model.ShoppingList;
import com.grocerymanager.api.model.User;
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
public interface ShoppingListRepository extends JpaRepository<ShoppingList, Long> {
    List<ShoppingList> findAllByUser(User user);

    // Find by ID and user with lock to prevent race conditions
    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<ShoppingList> findByIdAndUser(Long id, User user);

    // Find by syncId with lock
    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<ShoppingList> findBySyncIdAndUser(String syncId, User user);

    // Check if a list exists by syncId and user
    boolean existsBySyncIdAndUser(String syncId, User user);

    // Update a list directly by syncId
    @Modifying
    @Query("UPDATE ShoppingList l SET l.name = :name, l.updatedAt = :updatedAt, " +
            "l.lastSynced = :lastSynced WHERE l.syncId = :syncId AND l.user = :user")
    int updateBySyncIdAndUser(@Param("syncId") String syncId,
                              @Param("name") String name,
                              @Param("updatedAt") LocalDateTime updatedAt,
                              @Param("lastSynced") LocalDateTime lastSynced,
                              @Param("user") User user);

    List<ShoppingList> findByUserAndLastSyncedAfter(User user, LocalDateTime lastSynced);
}