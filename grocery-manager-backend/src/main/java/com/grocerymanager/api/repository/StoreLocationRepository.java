package com.grocerymanager.api.repository;

import com.grocerymanager.api.model.StoreLocation;
import com.grocerymanager.api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface StoreLocationRepository extends JpaRepository<StoreLocation, Long> {
    List<StoreLocation> findAllByUser(User user);

    Optional<StoreLocation> findByGeofenceId(String geofenceId);

    Optional<StoreLocation> findByIdAndUser(Long id, User user);

    Optional<StoreLocation> findBySyncIdAndUser(String syncId, User user);

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