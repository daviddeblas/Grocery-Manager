package com.grocerymanager.api.service.sync;

import com.grocerymanager.api.dto.*;
import com.grocerymanager.api.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Main synchronization service that orchestrates the bidirectional synchronization
 * between client devices and the server.
 *
 * This service delegates specific synchronization tasks to specialized services.
 */
@Service
public class SyncService {
    private static final Logger logger = LoggerFactory.getLogger(SyncService.class);

    @Autowired
    private ShoppingListSyncService listSyncService;

    @Autowired
    private ShoppingItemSyncService itemSyncService;

    @Autowired
    private StoreLocationSyncService storeSyncService;

    @Autowired
    private DeletedItemSyncService deletedItemSyncService;

    /**
     * Main synchronization method that processes client changes and returns merged data.
     * <p>
     * This method handles the complete synchronization workflow:
     *  - Process deleted items first
     *  - Synchronize shopping lists
     *  - Synchronize shopping items
     *  - Synchronize store locations
     *  - Retrieve server-side changes since last sync
     *  - Merge all changes into a single response
     *
     * Each step is executed in a separate transaction to ensure isolation of failures.
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
    public SyncResponse synchronize(SyncRequest request, User user) {
        LocalDateTime syncTime = LocalDateTime.now();
        LocalDateTime clientLastSync = request.getLastSyncTimestamp();

        // Initialize empty result lists
        List<ShoppingListDto> updatedLists = new ArrayList<>();
        List<ShoppingItemDto> updatedItems = new ArrayList<>();
        List<StoreLocationDto> updatedStores = new ArrayList<>();

        // 1. Process deleted items first
        if (request.getDeletedItems() != null && !request.getDeletedItems().isEmpty()) {
            try {
                deletedItemSyncService.processDeletedItems(request.getDeletedItems(), user);
            } catch (Exception e) {
                logger.error("Error processing deleted items: {}", e.getMessage());
                // Continue execution even if there's an error with deleted items
            }
        }

        // 2. Process shopping lists
        if (request.getShoppingLists() != null && !request.getShoppingLists().isEmpty()) {
            try {
                updatedLists = listSyncService.syncShoppingListsInNewTransaction(request.getShoppingLists(), user, syncTime);
            } catch (Exception e) {
                logger.error("Error syncing shopping lists: {}", e.getMessage());
                // Continue with other sync operations
            }
        }

        // 3. Process shopping items
        if (request.getShoppingItems() != null && !request.getShoppingItems().isEmpty()) {
            try {
                updatedItems = itemSyncService.syncShoppingItemsInNewTransaction(request.getShoppingItems(), user, syncTime);
            } catch (Exception e) {
                logger.error("Error syncing shopping items: {}", e.getMessage());
                // Continue with other sync operations
            }
        }

        // 4. Process store locations
        if (request.getStoreLocations() != null && !request.getStoreLocations().isEmpty()) {
            try {
                updatedStores = storeSyncService.syncStoreLocationsInNewTransaction(request.getStoreLocations(), user, syncTime);
            } catch (Exception e) {
                logger.error("Error syncing store locations: {}", e.getMessage());
                // Continue with other sync operations
            }
        }

        // 5. Get changes from server that were made since the last sync
        List<ShoppingListDto> serverLists = new ArrayList<>();
        List<ShoppingItemDto> serverItems = new ArrayList<>();
        List<StoreLocationDto> serverStores = new ArrayList<>();

        try {
            serverLists = listSyncService.getChangedListsFromServer(user, clientLastSync);
        } catch (Exception e) {
            logger.error("Error getting server lists: {}", e.getMessage());
        }

        try {
            serverItems = itemSyncService.getChangedItemsFromServer(user, clientLastSync);
        } catch (Exception e) {
            logger.error("Error getting server items: {}", e.getMessage());
        }

        try {
            serverStores = storeSyncService.getChangedStoresFromServer(user, clientLastSync);
        } catch (Exception e) {
            logger.error("Error getting server stores: {}", e.getMessage());
        }

        // 6. Create response with merged data
        return new SyncResponse(
                syncTime,
                mergeLists(updatedLists, serverLists),
                mergeItems(updatedItems, serverItems),
                mergeStores(updatedStores, serverStores)
        );
    }

    /**
     * Merges two lists of shopping lists avoiding duplicates based on syncId.
     */
    private List<ShoppingListDto> mergeLists(List<ShoppingListDto> list1, List<ShoppingListDto> list2) {
        List<ShoppingListDto> result = new ArrayList<>(list1);

        for (ShoppingListDto item : list2) {
            if (item.getSyncId() != null &&
                    result.stream().noneMatch(i -> item.getSyncId().equals(i.getSyncId()))) {
                result.add(item);
            }
        }

        return result;
    }

    /**
     * Merges two lists of shopping items avoiding duplicates based on syncId.
     */
    private List<ShoppingItemDto> mergeItems(List<ShoppingItemDto> list1, List<ShoppingItemDto> list2) {
        List<ShoppingItemDto> result = new ArrayList<>(list1);

        for (ShoppingItemDto item : list2) {
            if (item.getSyncId() != null &&
                    result.stream().noneMatch(i -> item.getSyncId().equals(i.getSyncId()))) {
                result.add(item);
            }
        }

        return result;
    }

    /**
     * Merges two lists of store locations avoiding duplicates based on syncId.
     */
    private List<StoreLocationDto> mergeStores(List<StoreLocationDto> list1, List<StoreLocationDto> list2) {
        List<StoreLocationDto> result = new ArrayList<>(list1);

        for (StoreLocationDto item : list2) {
            if (item.getSyncId() != null &&
                    result.stream().noneMatch(i -> item.getSyncId().equals(i.getSyncId()))) {
                result.add(item);
            }
        }

        return result;
    }
}