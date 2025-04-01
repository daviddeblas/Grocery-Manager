package com.grocerymanager.api.service.sync;

import com.grocerymanager.api.dto.DeletedItemDto;
import com.grocerymanager.api.model.User;
import com.grocerymanager.api.repository.ShoppingItemRepository;
import com.grocerymanager.api.repository.ShoppingListRepository;
import com.grocerymanager.api.repository.StoreLocationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service responsible for processing deleted items during synchronization.
 * Handles the removal of entities that were deleted on client devices.
 */
@Service
public class DeletedItemSyncService {
    private static final Logger logger = LoggerFactory.getLogger(DeletedItemSyncService.class);

    @Autowired
    private ShoppingItemRepository itemRepository;

    @Autowired
    private ShoppingListRepository listRepository;

    @Autowired
    private StoreLocationRepository storeRepository;

    /**
     * Processes items that have been deleted on the client side.
     * For each deleted item, removes the corresponding server-side entity
     * after verifying that it belongs to the authenticated user.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processDeletedItems(List<DeletedItemDto> deletedItems, User user) {
        if (deletedItems == null || deletedItems.isEmpty()) {
            return;
        }

        logger.info("Processing {} deleted items", deletedItems.size());

        for (DeletedItemDto item : deletedItems) {
            try {
                processDeletedItem(item, user);
            } catch (Exception e) {
                logger.error("Error processing deleted item of type {} with syncId {}: {}",
                        item.getEntityType(), item.getSyncId(), e.getMessage());
                // Continue processing other items even if one fails
            }
        }
    }

    /**
     * Process a single deleted item based on its entity type.
     */
    private void processDeletedItem(DeletedItemDto item, User user) {
        if (item.getSyncId() == null) {
            logger.warn("Skipping deleted item with null syncId: {}", item);
            return;
        }

        logger.debug("Processing deleted item: Type={}, SyncID={}",
                item.getEntityType(), item.getSyncId());

        switch (item.getEntityType()) {
            case "SHOPPING_ITEM":
                deleteShoppingItem(item.getSyncId(), user);
                break;

            case "SHOPPING_LIST":
                deleteShoppingList(item.getSyncId(), user);
                break;

            case "STORE_LOCATION":
                deleteStoreLocation(item.getSyncId(), user);
                break;

            default:
                logger.warn("Unknown entity type for deletion: {}", item.getEntityType());
                break;
        }
    }

    /**
     * Delete a shopping item and verify it belongs to the correct user.
     */
    private void deleteShoppingItem(String syncId, User user) {
        itemRepository.findBySyncId(syncId)
                .ifPresent(foundItem -> {
                    // Verify that the item belongs to the user
                    if (foundItem.getShoppingList().getUser().getId().equals(user.getId())) {
                        itemRepository.delete(foundItem);
                        logger.debug("Deleted shopping item with syncId: {}", syncId);
                    } else {
                        logger.warn("Attempted to delete shopping item belonging to another user, syncId: {}", syncId);
                    }
                });
    }

    /**
     * Delete a shopping list and verify it belongs to the correct user.
     */
    private void deleteShoppingList(String syncId, User user) {
        listRepository.findBySyncIdAndUser(syncId, user)
                .ifPresent(list -> {
                    listRepository.delete(list);
                    logger.debug("Deleted shopping list with syncId: {}", syncId);
                });
    }

    /**
     * Delete a store location and verify it belongs to the correct user.
     */
    private void deleteStoreLocation(String syncId, User user) {
        storeRepository.findBySyncIdAndUser(syncId, user)
                .ifPresent(store -> {
                    storeRepository.delete(store);
                    logger.debug("Deleted store location with syncId: {}", syncId);
                });
    }
}