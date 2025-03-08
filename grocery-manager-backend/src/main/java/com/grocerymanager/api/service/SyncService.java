package com.grocerymanager.api.service;

import com.grocerymanager.api.dto.*;
import com.grocerymanager.api.model.ShoppingItem;
import com.grocerymanager.api.model.ShoppingList;
import com.grocerymanager.api.model.StoreLocation;
import com.grocerymanager.api.model.User;
import com.grocerymanager.api.repository.ShoppingItemRepository;
import com.grocerymanager.api.repository.ShoppingListRepository;
import com.grocerymanager.api.repository.StoreLocationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SyncService {

    @Autowired
    private ShoppingListRepository listRepository;

    @Autowired
    private ShoppingItemRepository itemRepository;

    @Autowired
    private StoreLocationRepository storeRepository;

    @Autowired
    private ShoppingListService listService;

    @Autowired
    private ShoppingItemService itemService;

    @Autowired
    private StoreLocationService storeService;

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
    public SyncResponse synchronize(SyncRequest request, User user) {
        LocalDateTime syncTime = LocalDateTime.now();
        LocalDateTime clientLastSync = request.getLastSyncTimestamp();

        // Initialize empty result lists
        List<ShoppingListDto> updatedLists = new ArrayList<>();
        List<ShoppingItemDto> updatedItems = new ArrayList<>();
        List<StoreLocationDto> updatedStores = new ArrayList<>();

        // Process deleted items in separate transaction to isolate potential errors
        if (request.getDeletedItems() != null && !request.getDeletedItems().isEmpty()) {
            try {
                processDeletedItems(request.getDeletedItems(), user);
            } catch (Exception e) {
                System.err.println("Error processing deleted items: " + e.getMessage());
                // Continue execution even if there's an error with deleted items
            }
        }

        // Process shopping lists
        if (request.getShoppingLists() != null && !request.getShoppingLists().isEmpty()) {
            try {
                updatedLists = syncShoppingListsInNewTransaction(request.getShoppingLists(), user, syncTime);
            } catch (Exception e) {
                System.err.println("Error syncing shopping lists: " + e.getMessage());
                // Continue with other sync operations
            }
        }

        // Process shopping items
        if (request.getShoppingItems() != null && !request.getShoppingItems().isEmpty()) {
            try {
                updatedItems = syncShoppingItemsInNewTransaction(request.getShoppingItems(), user, syncTime);
            } catch (Exception e) {
                System.err.println("Error syncing shopping items: " + e.getMessage());
                // Continue with other sync operations
            }
        }

        // Process store locations
        if (request.getStoreLocations() != null && !request.getStoreLocations().isEmpty()) {
            try {
                updatedStores = syncStoreLocationsInNewTransaction(request.getStoreLocations(), user, syncTime);
            } catch (Exception e) {
                System.err.println("Error syncing store locations: " + e.getMessage());
                // Continue with other sync operations
            }
        }

        // Get changes from server
        List<ShoppingListDto> serverLists = new ArrayList<>();
        List<ShoppingItemDto> serverItems = new ArrayList<>();
        List<StoreLocationDto> serverStores = new ArrayList<>();

        try {
            serverLists = getChangedListsFromServer(user, clientLastSync);
        } catch (Exception e) {
            System.err.println("Error getting server lists: " + e.getMessage());
        }

        try {
            serverItems = getChangedItemsFromServer(user, clientLastSync);
        } catch (Exception e) {
            System.err.println("Error getting server items: " + e.getMessage());
        }

        try {
            serverStores = getChangedStoresFromServer(user, clientLastSync);
        } catch (Exception e) {
            System.err.println("Error getting server stores: " + e.getMessage());
        }

        // Create the response with merged data
        return new SyncResponse(
                syncTime,
                mergeListsWithoutDuplicates(updatedLists, serverLists),
                mergeItemsWithoutDuplicates(updatedItems, serverItems),
                mergeStoresWithoutDuplicates(updatedStores, serverStores)
        );
    }

    // Ajoutez cette nouvelle méthode avec sa propre transaction
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public List<ShoppingListDto> syncShoppingListsInNewTransaction(List<ShoppingListDto> clientLists, User user, LocalDateTime syncTime) {
        return syncShoppingLists(clientLists, user, syncTime);
    }

    // Et des méthodes similaires pour les autres types de données
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public List<ShoppingItemDto> syncShoppingItemsInNewTransaction(List<ShoppingItemDto> clientItems, User user, LocalDateTime syncTime) {
        return syncShoppingItems(clientItems, user, syncTime);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public List<StoreLocationDto> syncStoreLocationsInNewTransaction(List<StoreLocationDto> clientStores, User user, LocalDateTime syncTime) {
        return syncStoreLocations(clientStores, user, syncTime);
    }

    @Transactional
    protected List<ShoppingListDto> syncShoppingLists(List<ShoppingListDto> clientLists, User user, LocalDateTime syncTime) {
        List<ShoppingListDto> result = new ArrayList<>();

        if (clientLists == null || clientLists.isEmpty()) {
            return result;
        }

        for (ShoppingListDto listDto : clientLists) {
            try {
                // Handle lists with or without syncId
                if (listDto.getSyncId() != null && !listDto.getSyncId().isEmpty()) {
                    // Check if this list already exists
                    boolean listExists = listRepository.existsBySyncIdAndUser(listDto.getSyncId(), user);

                    if (listExists) {
                        // List exists - try direct update first for better concurrency
                        int updated = listRepository.updateBySyncIdAndUser(
                                listDto.getSyncId(),
                                listDto.getName(),
                                syncTime,
                                syncTime,
                                user
                        );

                        if (updated > 0) {
                            // Update succeeded, fetch the updated list
                            Optional<ShoppingList> updatedList = listRepository.findBySyncIdAndUser(listDto.getSyncId(), user);
                            updatedList.ifPresent(list -> result.add(listService.convertToDto(list)));
                        } else {
                            // Direct update failed, try to fetch and update normally
                            Optional<ShoppingList> existingList = listRepository.findBySyncIdAndUser(listDto.getSyncId(), user);
                            if (existingList.isPresent()) {
                                ShoppingList list = existingList.get();

                                // Check if client data is newer
                                if (listDto.getUpdatedAt() != null &&
                                        (list.getUpdatedAt() == null || listDto.getUpdatedAt().isAfter(list.getUpdatedAt()))) {

                                    list.setName(listDto.getName());
                                    list.setUpdatedAt(syncTime);
                                    list.setLastSynced(syncTime);

                                    ShoppingList savedList = listRepository.save(list);
                                    result.add(listService.convertToDto(savedList));
                                } else {
                                    // Server data is newer, just return the existing list
                                    result.add(listService.convertToDto(list));
                                }
                            }
                        }
                    } else {
                        // List doesn't exist - create a new one
                        try {
                            ShoppingList newList = new ShoppingList();
                            newList.setName(listDto.getName());
                            newList.setUser(user);
                            newList.setSyncId(listDto.getSyncId());
                            newList.setCreatedAt(syncTime);
                            newList.setUpdatedAt(syncTime);
                            newList.setLastSynced(syncTime);

                            // Save in a separate try-catch to handle potential race conditions
                            try {
                                ShoppingList savedList = listRepository.save(newList);
                                result.add(listService.convertToDto(savedList));
                            } catch (Exception e) {
                                // If save fails due to constraint violation, try to update instead
                                if (e.getMessage() != null && e.getMessage().contains("duplicate key")) {
                                    // Try again, but this time update the existing list
                                    Optional<ShoppingList> racedList = listRepository.findBySyncIdAndUser(listDto.getSyncId(), user);
                                    if (racedList.isPresent()) {
                                        ShoppingList list = racedList.get();
                                        list.setName(listDto.getName());
                                        list.setUpdatedAt(syncTime);
                                        list.setLastSynced(syncTime);

                                        ShoppingList savedList = listRepository.save(list);
                                        result.add(listService.convertToDto(savedList));
                                    }
                                } else {
                                    // Re-throw if it's not a duplicate key issue
                                    throw e;
                                }
                            }
                        } catch (Exception ex) {
                            System.err.println("Error creating new list with syncId " + listDto.getSyncId() + ": " + ex.getMessage());
                        }
                    }
                } else {
                    // No syncId provided, generate one and create a new list
                    ShoppingList newList = new ShoppingList();
                    newList.setName(listDto.getName());
                    newList.setUser(user);
                    newList.setSyncId(UUID.randomUUID().toString()); // Generate new syncId
                    newList.setCreatedAt(syncTime);
                    newList.setUpdatedAt(syncTime);
                    newList.setLastSynced(syncTime);

                    ShoppingList savedList = listRepository.save(newList);
                    result.add(listService.convertToDto(savedList));
                }
            } catch (Exception e) {
                // Log error but continue processing other lists
                System.err.println("Error processing list " + listDto.getName() +
                        " with syncId " + listDto.getSyncId() + ": " + e.getMessage());
            }
        }

        return result;
    }

    private ShoppingList createNewShoppingList(ShoppingListDto listDto, User user, LocalDateTime syncTime) {
        ShoppingList list = new ShoppingList();
        list.setName(listDto.getName());
        list.setUser(user);
        list.setSyncId(listDto.getSyncId());
        list.setCreatedAt(syncTime);
        list.setUpdatedAt(syncTime);
        list.setLastSynced(syncTime);
        return listRepository.save(list);
    }

    @Transactional
    protected List<ShoppingItemDto> syncShoppingItems(List<ShoppingItemDto> clientItems, User user, LocalDateTime syncTime) {
        List<ShoppingItemDto> result = new ArrayList<>();

        if (clientItems == null || clientItems.isEmpty()) {
            return result;
        }

        for (ShoppingItemDto itemDto : clientItems) {
            try {
                // Process each item in its own transaction to avoid rollback affecting other items
                processSingleItem(itemDto, user, syncTime, result);
            } catch (Exception e) {
                // Log error but continue with other items
                System.err.println("Error processing item " + itemDto.getName() +
                        " with syncId " + itemDto.getSyncId() + ": " + e.getMessage());
            }
        }

        return result;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void processSingleItem(ShoppingItemDto itemDto, User user, LocalDateTime syncTime, List<ShoppingItemDto> result) throws InterruptedException {
        try {
            // Find the shopping list
            Optional<ShoppingList> listOptional = listRepository.findByIdAndUser(itemDto.getShoppingListId(), user);
            if (!listOptional.isPresent()) {
                return;
            }
            ShoppingList list = listOptional.get();

            // Check if this item already exists by syncId
            if (itemDto.getSyncId() != null && !itemDto.getSyncId().isEmpty()) {
                boolean exists = false;

                try {
                    // Use the native SQL upsert function instead of regular JPA methods
                    Long itemId = itemRepository.safeUpsertItem(
                            itemDto.getName(),
                            itemDto.getQuantity(),
                            itemDto.getUnitType(),
                            itemDto.isChecked(),
                            itemDto.getSortIndex(),
                            list.getId(),
                            itemDto.getSyncId(),
                            syncTime,
                            syncTime,
                            syncTime
                    );

                    if (itemId != null) {
                        Optional<ShoppingItem> savedItem = itemRepository.findById(itemId);
                        savedItem.ifPresent(item -> result.add(itemService.convertToDto(item)));
                        exists = true;
                    }
                } catch (Exception e) {
                    System.err.println("Upsert failed, trying alternative approach: " + e.getMessage());
                }

                // If upsert didn't work, try the traditional approach
                if (!exists) {
                    Optional<ShoppingItem> existingItem = itemRepository.findBySyncId(itemDto.getSyncId());

                    if (existingItem.isPresent()) {
                        // Update existing item
                        ShoppingItem item = existingItem.get();
                        item.setName(itemDto.getName());
                        item.setQuantity(itemDto.getQuantity());
                        item.setUnitType(itemDto.getUnitType());
                        item.setChecked(itemDto.isChecked());
                        item.setSortIndex(itemDto.getSortIndex());
                        item.setUpdatedAt(syncTime);
                        item.setLastSynced(syncTime);

                        ShoppingItem savedItem = itemRepository.save(item);
                        result.add(itemService.convertToDto(savedItem));
                    } else {
                        // Create new item
                        ShoppingItem newItem = new ShoppingItem();
                        newItem.setName(itemDto.getName());
                        newItem.setQuantity(itemDto.getQuantity());
                        newItem.setUnitType(itemDto.getUnitType());
                        newItem.setChecked(itemDto.isChecked());
                        newItem.setSortIndex(itemDto.getSortIndex());
                        newItem.setShoppingList(list);
                        newItem.setSyncId(itemDto.getSyncId());
                        newItem.setCreatedAt(syncTime);
                        newItem.setUpdatedAt(syncTime);
                        newItem.setLastSynced(syncTime);

                        try {
                            ShoppingItem savedItem = itemRepository.save(newItem);
                            result.add(itemService.convertToDto(savedItem));
                        } catch (Exception ex) {
                            // If we fail again due to concurrency, try one more final approach
                            if (ex.getMessage() != null && ex.getMessage().contains("duplicate key")) {
                                Thread.sleep(100); // Brief delay to let potential concurrent operation finish
                                existingItem = itemRepository.findBySyncId(itemDto.getSyncId());
                                if (existingItem.isPresent()) {
                                    result.add(itemService.convertToDto(existingItem.get()));
                                }
                            } else {
                                throw ex;
                            }
                        }
                    }
                }
            } else {
                // No syncId provided, generate a new one
                String newSyncId = UUID.randomUUID().toString();
                ShoppingItem newItem = new ShoppingItem();
                newItem.setName(itemDto.getName());
                newItem.setQuantity(itemDto.getQuantity());
                newItem.setUnitType(itemDto.getUnitType());
                newItem.setChecked(itemDto.isChecked());
                newItem.setSortIndex(itemDto.getSortIndex());
                newItem.setShoppingList(list);
                newItem.setSyncId(newSyncId);
                newItem.setCreatedAt(syncTime);
                newItem.setUpdatedAt(syncTime);
                newItem.setLastSynced(syncTime);

                ShoppingItem savedItem = itemRepository.save(newItem);
                result.add(itemService.convertToDto(savedItem));
            }
        } catch (Exception e) {
            System.err.println("Error in processSingleItem: " + e.getMessage());
            throw e; // Re-throw so the outer catch can handle it
        }
    }


    private List<StoreLocationDto> syncStoreLocations(List<StoreLocationDto> clientStores, User user, LocalDateTime syncTime) {
        List<StoreLocationDto> result = new ArrayList<>();

        if (clientStores == null || clientStores.isEmpty()) {
            return result;
        }

        for (StoreLocationDto storeDto : clientStores) {
            // First check by geofenceId which is unique
            Optional<StoreLocation> storeByGeofence = storeRepository.findByGeofenceId(storeDto.getGeofenceId());

            // If found by geofenceId, update this store rather than create a new one
            if (storeByGeofence.isPresent() && storeByGeofence.get().getUser().getId().equals(user.getId())) {
                StoreLocation store = storeByGeofence.get();
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
                continue;
            }


            // Case 1: Store without ID and without syncID (new store on the client)
            // Create a new store with a new syncID and add it to the result
            if (storeDto.getId() == null && (storeDto.getSyncId() == null || storeDto.getSyncId().isEmpty())) {
                storeDto.setSyncId(UUID.randomUUID().toString());
                StoreLocation store = createNewStoreLocation(storeDto, user, syncTime);
                result.add(storeService.convertToDto(store));
                continue;
            }

            // Case 2: Store with syncID (update or creation on the server)
            if (storeDto.getSyncId() != null && !storeDto.getSyncId().isEmpty()) {
                Optional<StoreLocation> existingStore = storeRepository.findBySyncIdAndUser(storeDto.getSyncId(), user);

                if (existingStore.isPresent()) {
                    // Update existing store
                    StoreLocation store = existingStore.get();

                    // Check if the client data is more recent
                    // If the client data is more recent, update the store and add it to the result
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
                } else {
                    // New store with predefined syncID
                    StoreLocation store = createNewStoreLocation(storeDto, user, syncTime);
                    result.add(storeService.convertToDto(store));
                }
            }
        }
        return result;
    }

    private StoreLocation createNewStoreLocation(StoreLocationDto storeDto, User user, LocalDateTime syncTime) {
        StoreLocation store = new StoreLocation();
        store.setName(storeDto.getName());
        store.setAddress(storeDto.getAddress());
        store.setLatitude(storeDto.getLatitude());
        store.setLongitude(storeDto.getLongitude());
        store.setGeofenceId(storeDto.getGeofenceId());
        store.setUser(user);
        store.setSyncId(storeDto.getSyncId());
        store.setCreatedAt(syncTime);
        store.setUpdatedAt(syncTime);
        store.setLastSynced(syncTime);
        return storeRepository.save(store);
    }

    private List<ShoppingListDto> getChangedListsFromServer(User user, LocalDateTime lastSync) {
        if (lastSync == null) {
            // If this is the first synchronization, return all lists
            return listRepository.findAllByUser(user).stream()
                    .map(listService::convertToDto)
                    .collect(Collectors.toList());
        }

        // Otherwise, only return lists modified since the last synchronization
        return listRepository.findByUserAndLastSyncedAfter(user, lastSync).stream()
                .map(listService::convertToDto)
                .collect(Collectors.toList());
    }

    private List<ShoppingItemDto> getChangedItemsFromServer(User user, LocalDateTime lastSync) {
        List<ShoppingItemDto> result = new ArrayList<>();

        List<ShoppingList> userLists = listRepository.findAllByUser(user);

        if (lastSync == null) {
            // If this is the first synchronization, return all elements
            for (ShoppingList list : userLists) {
                result.addAll(itemRepository.findAllByShoppingList(list).stream()
                        .map(itemService::convertToDto)
                        .toList());
            }
        } else {
            // Otherwise, return only elements modified since the last synchronization
            for (ShoppingList list : userLists) {
                result.addAll(itemRepository.findByShoppingListAndLastSyncedAfter(list, lastSync).stream()
                        .map(itemService::convertToDto)
                        .toList());
            }
        }

        return result;
    }

    private List<StoreLocationDto> getChangedStoresFromServer(User user, LocalDateTime lastSync) {
        if (lastSync == null) {
            // If this is the first sync, return all stores
            return storeRepository.findAllByUser(user).stream()
                    .map(storeService::convertToDto)
                    .collect(Collectors.toList());
        }

        // Otherwise, return only stores modified since the last synchronization
        return storeRepository.findByUserAndLastSyncedAfter(user, lastSync).stream()
                .map(storeService::convertToDto)
                .collect(Collectors.toList());
    }

    private List<ShoppingListDto> mergeListsWithoutDuplicates(List<ShoppingListDto> list1, List<ShoppingListDto> list2) {
        List<ShoppingListDto> result = new ArrayList<>(list1);

        for (ShoppingListDto item : list2) {
            if (result.stream().noneMatch(i -> i.getSyncId().equals(item.getSyncId()))) {
                result.add(item);
            }
        }

        return result;
    }

    private List<ShoppingItemDto> mergeItemsWithoutDuplicates(List<ShoppingItemDto> list1, List<ShoppingItemDto> list2) {
        List<ShoppingItemDto> result = new ArrayList<>(list1);

        for (ShoppingItemDto item : list2) {
            if (result.stream().noneMatch(i -> i.getSyncId().equals(item.getSyncId()))) {
                result.add(item);
            }
        }

        return result;
    }

    private List<StoreLocationDto> mergeStoresWithoutDuplicates(List<StoreLocationDto> list1, List<StoreLocationDto> list2) {
        List<StoreLocationDto> result = new ArrayList<>(list1);

        for (StoreLocationDto item : list2) {
            if (result.stream().noneMatch(i -> i.getSyncId().equals(item.getSyncId()))) {
                result.add(item);
            }
        }

        return result;
    }

    private void processDeletedItems(List<DeletedItemDto> deletedItems, User user) {
        if (deletedItems == null || deletedItems.isEmpty()) {
            return;
        }

        System.out.println("Traitement de " + deletedItems.size() + " éléments supprimés");

        for (DeletedItemDto item : deletedItems) {
            switch (item.getEntityType()) {
                case "SHOPPING_ITEM":
                    if (item.getSyncId() != null) {
                        System.out.println("  - Type: " + item.getEntityType() + ", SyncID: " + item.getSyncId());
                        itemRepository.findBySyncId(item.getSyncId())
                                .ifPresent(foundItem -> {
                                    // Vérifier que l'élément appartient à l'utilisateur
                                    if (foundItem.getShoppingList().getUser().getId().equals(user.getId())) {
                                        itemRepository.delete(foundItem);
                                    }
                                });
                    }
                    break;

                case "SHOPPING_LIST":
                    if (item.getSyncId() != null) {
                        listRepository.findBySyncIdAndUser(item.getSyncId(), user)
                                .ifPresent(listRepository::delete);
                    }
                    break;

                case "STORE_LOCATION":
                    if (item.getSyncId() != null) {
                        storeRepository.findBySyncIdAndUser(item.getSyncId(), user)
                                .ifPresent(storeRepository::delete);
                    }
                    break;

                default:
                    break;
            }
        }
    }
}