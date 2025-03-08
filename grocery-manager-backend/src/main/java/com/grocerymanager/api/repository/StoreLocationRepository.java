package com.grocerymanager.api.repository;

import com.grocerymanager.api.model.StoreLocation;
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
public interface StoreLocationRepository extends JpaRepository<StoreLocation, Long> {
    List<StoreLocation> findAllByUser(User user);

    // Find stores by geofenceId with locking
    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<StoreLocation> findByGeofenceId(String geofenceId);

    // Find by ID and user with locking
    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<StoreLocation> findByIdAndUser(Long id, User user);

    // Find by syncId with locking
    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<StoreLocation> findBySyncIdAndUser(String syncId, User user);

    // Check if a store exists by syncId and user
    boolean existsBySyncIdAndUser(String syncId, User user);

    // Update a store directly by syncId
    @Modifying
    @Query("UPDATE StoreLocation s SET s.name = :name, s.address = :address, " +
            "s.latitude = :latitude, s.longitude = :longitude, " +
            "s.updatedAt = :updatedAt, s.lastSynced = :lastSynced " +
            "WHERE s.syncId = :syncId AND s.user = :user")
    int updateBySyncIdAndUser(@Param("syncId") String syncId,
                              @Param("name") String name,
                              @Param("address") String address,
                              @Param("latitude") Double latitude,
                              @Param("longitude") Double longitude,
                              @Param("updatedAt") LocalDateTime updatedAt,
                              @Param("lastSynced") LocalDateTime lastSynced,
                              @Param("user") User user);

    @Query("SELECT s FROM StoreLocation s WHERE s.user = :user AND " +
            "(:latitude - s.latitude) * (:latitude - s.latitude) + " +
            "(:longitude - s.longitude) * (:longitude - s.longitude) < :radiusSquared")
    List<StoreLocation> findNearbyStores(
            @Param("user") User user,
            @Param("latitude") Double latitude,
            @Param("longitude") Double longitude,
            @Param("radiusSquared") Double radiusSquared
    );

    List<StoreLocation> findByUserAndLastSyncedAfter(User user, LocalDateTime lastSynced);
}