package com.grocerymanager.api.service.sync;

import com.grocerymanager.api.dto.StoreLocationDto;
import com.grocerymanager.api.model.StoreLocation;
import com.grocerymanager.api.model.User;
import com.grocerymanager.api.repository.StoreLocationRepository;
import com.grocerymanager.api.service.StoreLocationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service responsible for synchronizing store locations between client devices and the server.
 */
@Service
public class StoreLocationSyncService {
    private static final Logger logger = LoggerFactory.getLogger(StoreLocationSyncService.class);

    @Autowired
    private StoreLocationRepository storeRepository;

    @Autowired
    private StoreLocationService storeService;

    /**
     * Processes store locations synchronization in a new independent transaction.
     * This allows failures in store synchronization to be isolated from the main sync process.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<StoreLocationDto> syncStoreLocationsInNewTransaction(
            List<StoreLocationDto> clientStores, User user, LocalDateTime syncTime) {
        return syncStoreLocations(clientStores, user, syncTime);
    }

    /**
     * Synchronizes store locations from client to server.
     *
     * This method handles store locations with special consideration for:
     * - Geofence identifiers which must remain unique
     * - Geographic coordinates for duplicate detection
     * - Different detection strategies for existing stores
     */
    @Transactional
    protected List<StoreLocationDto> syncStoreLocations(
            List<StoreLocationDto> clientStores, User user, LocalDateTime syncTime) {
        List<StoreLocationDto> result = new ArrayList<>();

        if (clientStores == null || clientStores.isEmpty()) {
            return result;
        }

        for (StoreLocationDto storeDto : clientStores) {
            try {
                processStoreLocation(storeDto, user, syncTime, result);
            } catch (Exception e) {
                logger.error("Error processing store {} with syncId {}: {}",
                        storeDto.getName(), storeDto.getSyncId(), e.getMessage());
            }
        }

        return result;
    }

    /**
     * Process a single store location, handling all the various ways to identify existing stores.
     */
    private void processStoreLocation(StoreLocationDto storeDto, User user,
                                      LocalDateTime syncTime, List<StoreLocationDto> result) {
        // First check by geofenceId which is unique
        Optional<StoreLocation> storeByGeofence = findStoreByGeofenceId(storeDto, user);
        if (storeByGeofence.isPresent()) {
            updateExistingStore(storeByGeofence.get(), storeDto, syncTime, result);
            return;
        }

        // Case 1: Store without ID and without syncID (new store on the client)
        if (storeDto.getId() == null && (storeDto.getSyncId() == null || storeDto.getSyncId().isEmpty())) {
            createNewStore(storeDto, user, syncTime, result);
            return;
        }

        // Case 2: Store with syncID (update or creation on the server)
        if (storeDto.getSyncId() != null && !storeDto.getSyncId().isEmpty()) {
            Optional<StoreLocation> existingStore = storeRepository.findBySyncIdAndUser(storeDto.getSyncId(), user);

            if (existingStore.isPresent()) {
                handleStoreConflict(existingStore.get(), storeDto, syncTime, result);
            } else {
                // New store with predefined syncID
                createNewStore(storeDto, user, syncTime, result);
            }
        }
    }

    /**
     * Find a store location by its geofence ID and user.
     */
    private Optional<StoreLocation> findStoreByGeofenceId(StoreLocationDto storeDto, User user) {
        if (storeDto.getGeofenceId() == null || storeDto.getGeofenceId().isEmpty()) {
            return Optional.empty();
        }

        Optional<StoreLocation> storeByGeofence = storeRepository.findByGeofenceId(storeDto.getGeofenceId());
        return storeByGeofence.filter(store -> store.getUser().getId().equals(user.getId()));
    }

    /**
     * Update an existing store from a DTO.
     */
    private void updateExistingStore(StoreLocation store, StoreLocationDto storeDto,
                                     LocalDateTime syncTime, List<StoreLocationDto> result) {
        store.setName(storeDto.getName());
        store.setAddress(storeDto.getAddress());
        store.setLatitude(storeDto.getLatitude());
        store.setLongitude(storeDto.getLongitude());
        store.setUpdatedAt(syncTime);
        store.setLastSynced(syncTime);

        // If syncId was not set, set it now
        if (store.getSyncId() == null && storeDto.getSyncId() != null) {
            store.setSyncId(storeDto.getSyncId());
        }

        result.add(storeService.convertToDto(storeRepository.save(store)));
    }

    /**
     * Handle conflict resolution for store updates based on timestamps.
     */
    private void handleStoreConflict(StoreLocation store, StoreLocationDto storeDto,
                                     LocalDateTime syncTime, List<StoreLocationDto> result) {
        // Check if the client data is more recent
        if (storeDto.getUpdatedAt() != null &&
                (store.getUpdatedAt() == null || storeDto.getUpdatedAt().isAfter(store.getUpdatedAt()))) {
            store.setName(storeDto.getName());
            store.setAddress(storeDto.getAddress());
            store.setLatitude(storeDto.getLatitude());
            store.setLongitude(storeDto.getLongitude());
            store.setUpdatedAt(syncTime);
            store.setLastSynced(syncTime);
            result.add(storeService.convertToDto(storeRepository.save(store)));
        } else {
            // Server data is more recent or of same date, so we return the server version
            result.add(storeService.convertToDto(store));
        }
    }

    /**
     * Creates a new store location entity from a DTO.
     */
    private void createNewStore(StoreLocationDto storeDto, User user,
                                LocalDateTime syncTime, List<StoreLocationDto> result) {
        StoreLocation store = new StoreLocation();
        store.setName(storeDto.getName());
        store.setAddress(storeDto.getAddress());
        store.setLatitude(storeDto.getLatitude());
        store.setLongitude(storeDto.getLongitude());
        store.setGeofenceId(storeDto.getGeofenceId());
        store.setUser(user);

        // Use existing syncId or generate a new one
        store.setSyncId(storeDto.getSyncId() != null ?
                storeDto.getSyncId() : UUID.randomUUID().toString());

        store.setCreatedAt(syncTime);
        store.setUpdatedAt(syncTime);
        store.setLastSynced(syncTime);

        result.add(storeService.convertToDto(storeRepository.save(store)));
    }

    /**
     * Retrieves store locations that have changed on the server since the last synchronization.
     */
    @Transactional(readOnly = true)
    public List<StoreLocationDto> getChangedStoresFromServer(User user, LocalDateTime lastSync) {
        if (lastSync == null) {
            // If this is the first sync, return all stores
            return storeRepository.findAllByUser(user).stream()
                    .map(storeService::convertToDto)
                    .toList();
        }

        // Otherwise, return only stores modified since the last synchronization
        return storeRepository.findByUserAndLastSyncedAfter(user, lastSync).stream()
                .map(storeService::convertToDto)
                .toList();
    }
}