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

    @Transactional
    public SyncResponse synchronize(SyncRequest request, User user) {
        LocalDateTime syncTime = LocalDateTime.now();
        LocalDateTime clientLastSync = request.getLastSyncTimestamp();

        if (request.getDeletedItems() != null && !request.getDeletedItems().isEmpty()) {
            processDeletedItems(request.getDeletedItems(), user);
        }


        // Grocery lists
        List<ShoppingListDto> updatedLists = syncShoppingLists(request.getShoppingLists(), user, syncTime);
        List<ShoppingListDto> serverLists = getChangedListsFromServer(user, clientLastSync);

        // Items in the lists
        List<ShoppingItemDto> updatedItems = syncShoppingItems(request.getShoppingItems(), user, syncTime);
        List<ShoppingItemDto> serverItems = getChangedItemsFromServer(user, clientLastSync);

        // Stores
        List<StoreLocationDto> updatedStores = syncStoreLocations(request.getStoreLocations(), user, syncTime);
        List<StoreLocationDto> serverStores = getChangedStoresFromServer(user, clientLastSync);

        return new SyncResponse(
                syncTime,
                mergeListsWithoutDuplicates(updatedLists, serverLists),
                mergeItemsWithoutDuplicates(updatedItems, serverItems),
                mergeStoresWithoutDuplicates(updatedStores, serverStores)
        );
    }

    private List<ShoppingListDto> syncShoppingLists(List<ShoppingListDto> clientLists, User user, LocalDateTime syncTime) {
        List<ShoppingListDto> result = new ArrayList<>();

        if (clientLists == null || clientLists.isEmpty()) {
            return result;
        }

        for (ShoppingListDto listDto : clientLists) {
            // Case 1: List without ID and without syncID (so -> new list on client)
            // Create a new list with a new syncID and add it to the result
            if (listDto.getId() == null && (listDto.getSyncId() == null || listDto.getSyncId().isEmpty())) {
                listDto.setSyncId(UUID.randomUUID().toString());
                ShoppingList list = createNewShoppingList(listDto, user, syncTime);
                result.add(listService.convertToDto(list));
                continue;
            }

            // Case 2: List with syncID (updating or creating on server)
            if (listDto.getSyncId() != null && !listDto.getSyncId().isEmpty()) {
                Optional<ShoppingList> existingList = listRepository.findBySyncIdAndUser(listDto.getSyncId(), user);

                if (existingList.isPresent()) {
                    // Update existing list
                    ShoppingList list = existingList.get();

                    // Check if customer data is more recent
                    // If the client data is more recent, update the list and add it to the result
                    if (listDto.getUpdatedAt() != null &&
                            (list.getUpdatedAt() == null || listDto.getUpdatedAt().isAfter(list.getUpdatedAt()))) {
                        list.setName(listDto.getName());
                        list.setUpdatedAt(syncTime);
                        list.setLastSynced(syncTime);
                        result.add(listService.convertToDto(listRepository.save(list)));
                    } else {
                        // Server data is more recent or of same date, so we return the server version
                        result.add(listService.convertToDto(list));
                    }
                } else {
                    // New list with predefined syncID
                    ShoppingList list = createNewShoppingList(listDto, user, syncTime);
                    result.add(listService.convertToDto(list));
                }
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

    private List<ShoppingItemDto> syncShoppingItems(List<ShoppingItemDto> clientItems, User user, LocalDateTime syncTime) {
        List<ShoppingItemDto> result = new ArrayList<>();

        if (clientItems == null || clientItems.isEmpty()) {
            return result;
        }

        for (ShoppingItemDto itemDto : clientItems) {
            // If list ID is -1, search for user's first list
            if (itemDto.getShoppingListId() <= 0) {
                List<ShoppingList> userLists = listRepository.findAllByUser(user);
                if (!userLists.isEmpty()) {
                    ShoppingList defaultList = userLists.get(0);
                    itemDto.setShoppingListId(defaultList.getId());
                } else {
                    // Unable to find a list, ignoring this item
                    continue;
                }
            }

            // Case 1: Item without ID and without syncID (new item on the client)
            if (itemDto.getId() == null && (itemDto.getSyncId() == null || itemDto.getSyncId().isEmpty())) {
                Optional<ShoppingList> list = listRepository.findById(itemDto.getShoppingListId());
                if (list.isPresent() && list.get().getUser().getId().equals(user.getId())) {
                    itemDto.setSyncId(UUID.randomUUID().toString());
                    ShoppingItem item = createNewShoppingItem(itemDto, list.get(), syncTime);
                    result.add(itemService.convertToDto(item));
                }
                continue;
            }

            // Case 2: Item with syncID (update or creation on server)
            if (itemDto.getSyncId() != null && !itemDto.getSyncId().isEmpty()) {
                Optional<ShoppingItem> existingItem = itemRepository.findBySyncId(itemDto.getSyncId());

                if (existingItem.isPresent()) {
                    ShoppingItem item = existingItem.get();
                    ShoppingList list = item.getShoppingList();

                    // Verify that the item belongs to the user
                    if (!list.getUser().getId().equals(user.getId())) {
                        continue;
                    }

                    // Check if customer data is more recent
                    if (itemDto.getUpdatedAt() != null &&
                            (item.getUpdatedAt() == null || itemDto.getUpdatedAt().isAfter(item.getUpdatedAt()))) {
                        item.setName(itemDto.getName());
                        item.setQuantity(itemDto.getQuantity());
                        item.setUnitType(itemDto.getUnitType());
                        item.setChecked(itemDto.isChecked());
                        item.setSortIndex(itemDto.getSortIndex());
                        item.setUpdatedAt(syncTime);
                        item.setLastSynced(syncTime);
                        result.add(itemService.convertToDto(itemRepository.save(item)));
                    } else {
                        // Server data is more recent or of same date
                        result.add(itemService.convertToDto(item));
                    }
                } else {
                    // New item with predefined syncID
                    Optional<ShoppingList> list = listRepository.findById(itemDto.getShoppingListId());
                    if (list.isPresent() && list.get().getUser().getId().equals(user.getId())) {
                        ShoppingItem item = createNewShoppingItem(itemDto, list.get(), syncTime);
                        result.add(itemService.convertToDto(item));
                    }
                }
            }
        }
        return result;
    }

    private ShoppingItem createNewShoppingItem(ShoppingItemDto itemDto, ShoppingList list, LocalDateTime syncTime) {
        ShoppingItem item = new ShoppingItem();
        item.setName(itemDto.getName());
        item.setQuantity(itemDto.getQuantity());
        item.setUnitType(itemDto.getUnitType());
        item.setChecked(itemDto.isChecked());
        item.setSortIndex(itemDto.getSortIndex());
        item.setShoppingList(list);
        item.setSyncId(itemDto.getSyncId());
        item.setCreatedAt(syncTime);
        item.setUpdatedAt(syncTime);
        item.setLastSynced(syncTime);
        return itemRepository.save(item);
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