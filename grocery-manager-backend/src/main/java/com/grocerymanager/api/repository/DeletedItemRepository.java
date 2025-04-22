package com.grocerymanager.api.repository;

import com.grocerymanager.api.model.DeletedItem;
import com.grocerymanager.api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for managing deleted items in the database.
 * Tracks items that have been deleted for synchronization purposes.
 */
@Repository
public interface DeletedItemRepository extends JpaRepository<DeletedItem, Long> {

    /**
     * Find all deleted items for a specific user.
     */
    List<DeletedItem> findByUser(User user);

    /**
     * Find deleted items for a user with a specific sync status.
     */
    List<DeletedItem> findByUserAndSynced(User user, boolean synced);

    /**
     * Find a deleted item by its syncId and user.
     */
    DeletedItem findBySyncIdAndUser(String syncId, User user);

    /**
     * Update the sync status for multiple deleted items.
     */
    @Modifying
    @Query("UPDATE DeletedItem d SET d.synced = :synced WHERE d.id IN :ids")
    int updateSyncedStatus(@Param("ids") List<Long> ids, @Param("synced") boolean synced);

    /**
     * Delete all items with a specific sync status.
     */
    @Modifying
    @Query("DELETE FROM DeletedItem d WHERE d.synced = :synced")
    int deleteBySynced(@Param("synced") boolean synced);

    /**
     * Find deleted items by entity type and original ID.
     */
    List<DeletedItem> findByEntityTypeAndOriginalId(String entityType, Long originalId);

    /**
     * Find deleted items that were deleted before a specific time.
     * 
     * @param cutoff The cutoff time - only items deleted before this time will be returned
     * @return List of deleted items with deletedAt before the cutoff time
     */
    @Query("SELECT d FROM DeletedItem d WHERE d.deletedAt < :cutoff")
    List<DeletedItem> findByDeletedAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
