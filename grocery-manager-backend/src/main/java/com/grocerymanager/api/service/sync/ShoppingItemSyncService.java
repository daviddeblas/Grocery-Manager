package com.grocerymanager.api.service.sync;

import com.grocerymanager.api.dto.ShoppingItemDto;
import com.grocerymanager.api.model.ShoppingItem;
import com.grocerymanager.api.model.ShoppingList;
import com.grocerymanager.api.model.User;
import com.grocerymanager.api.repository.ShoppingItemRepository;
import com.grocerymanager.api.repository.ShoppingListRepository;
import com.grocerymanager.api.service.ShoppingItemService;
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
 * Service responsible for synchronizing shopping items between client devices and the server.
 */
@Service
public class ShoppingItemSyncService {
    private static final Logger logger = LoggerFactory.getLogger(ShoppingItemSyncService.class);

    @Autowired
    private ShoppingItemRepository itemRepository;

    @Autowired
    private ShoppingListRepository listRepository;

    @Autowired
    private ShoppingItemService itemService;

    /**
     * Processes shopping items synchronization in a new independent transaction.
     * This allows failures in item synchronization to be isolated from the main sync process.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<ShoppingItemDto> syncShoppingItemsInNewTransaction(
            List<ShoppingItemDto> clientItems, User user, LocalDateTime syncTime) {
        return syncShoppingItems(clientItems, user, syncTime);
    }

    /**
     * Synchronizes shopping items from client to server.
     *
     * This method processes each shopping item individually and handles:
     * - Creating new items
     * - Updating existing items
     * - Utilizing native SQL upsert functionality when available
     */
    @Transactional
    protected List<ShoppingItemDto> syncShoppingItems(
            List<ShoppingItemDto> clientItems, User user, LocalDateTime syncTime) {
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
                logger.error("Error processing item {} with syncId {}: {}",
                        itemDto.getName(), itemDto.getSyncId(), e.getMessage());
            }
        }

        return result;
    }

    /**
     * Processes a single shopping item in a dedicated transaction.
     *
     * This method attempts multiple strategies to insert or update an item:
     * - First tries a native SQL upsert for atomic operation
     * - Falls back to a find-then-update pattern if upsert fails
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void processSingleItem(ShoppingItemDto itemDto, User user,
                                     LocalDateTime syncTime, List<ShoppingItemDto> result) throws InterruptedException {
        try {
            // Find the shopping list
            Optional<ShoppingList> listOptional = listRepository.findByIdAndUser(itemDto.getShoppingListId(), user);
            if (listOptional.isEmpty()) {
                logger.warn("Cannot find list with ID {} for user {}", itemDto.getShoppingListId(), user.getUsername());
                return;
            }
            ShoppingList list = listOptional.get();

            // Check if this item already exists by syncId
            if (itemDto.getSyncId() != null && !itemDto.getSyncId().isEmpty()) {
                tryUpsertItem(itemDto, list, syncTime, result);
            } else {
                // No syncId provided, generate a new one
                createNewItem(itemDto, list, syncTime, result);
            }
        } catch (Exception e) {
            logger.error("Error in processSingleItem: {}", e.getMessage());
            throw e; // Re-throw so the outer catch can handle it
        }
    }

    /**
     * Tries to use the native SQL upsert function first, then falls back to standard JPA methods.
     */
    private void tryUpsertItem(ShoppingItemDto itemDto, ShoppingList list,
                               LocalDateTime syncTime, List<ShoppingItemDto> result) throws InterruptedException {
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
            logger.warn("Upsert failed, trying alternative approach: {}", e.getMessage());
        }

        // If upsert didn't work, try the traditional approach
        if (!exists) {
            tryTraditionalUpdateOrInsert(itemDto, list, syncTime, result);
        }
    }

    /**
     * Uses traditional JPA methods to find and update an item, or insert if not found.
     */
    private void tryTraditionalUpdateOrInsert(ShoppingItemDto itemDto, ShoppingList list,
                                              LocalDateTime syncTime, List<ShoppingItemDto> result) throws InterruptedException {
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
            createItemWithExistingSyncId(itemDto, list, syncTime, result);
        }
    }

    /**
     * Creates a new item with an existing syncId.
     * Handles potential race conditions with retries.
     */
    private void createItemWithExistingSyncId(ShoppingItemDto itemDto, ShoppingList list,
                                              LocalDateTime syncTime, List<ShoppingItemDto> result) throws InterruptedException {
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
                Optional<ShoppingItem> existingItem = itemRepository.findBySyncId(itemDto.getSyncId());
                existingItem.ifPresent(shoppingItem -> result.add(itemService.convertToDto(shoppingItem)));
            } else {
                throw ex;
            }
        }
    }

    /**
     * Creates a new item with a newly generated syncId.
     */
    private void createNewItem(ShoppingItemDto itemDto, ShoppingList list,
                               LocalDateTime syncTime, List<ShoppingItemDto> result) {
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

    /**
     * Retrieves shopping items that have changed on the server since the last synchronization.
     */
    @Transactional(readOnly = true)
    public List<ShoppingItemDto> getChangedItemsFromServer(User user, LocalDateTime lastSync) {
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
}