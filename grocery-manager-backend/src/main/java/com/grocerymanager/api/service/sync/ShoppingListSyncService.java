package com.grocerymanager.api.service.sync;

import com.grocerymanager.api.dto.ShoppingListDto;
import com.grocerymanager.api.model.ShoppingList;
import com.grocerymanager.api.model.User;
import com.grocerymanager.api.repository.ShoppingListRepository;
import com.grocerymanager.api.service.ShoppingListService;
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
 * Service responsible for synchronizing shopping lists between client devices and the server.
 */
@Service
public class ShoppingListSyncService {
    private static final Logger logger = LoggerFactory.getLogger(ShoppingListSyncService.class);

    @Autowired
    private ShoppingListRepository listRepository;

    @Autowired
    private ShoppingListService listService;

    /**
     * Processes shopping lists synchronization in a new independent transaction.
     * This allows failures in list synchronization to be isolated from the main sync process.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<ShoppingListDto> syncShoppingListsInNewTransaction(
            List<ShoppingListDto> clientLists, User user, LocalDateTime syncTime) {
        return syncShoppingLists(clientLists, user, syncTime);
    }

    /**
     * Synchronizes shopping lists from client to server.
     *
     * This method handles:
     * - Creating new lists
     * - Updating existing lists
     * - Handling conflict resolution based on timestamps
     */
    @Transactional
    protected List<ShoppingListDto> syncShoppingLists(
            List<ShoppingListDto> clientLists, User user, LocalDateTime syncTime) {
        List<ShoppingListDto> result = new ArrayList<>();

        if (clientLists == null || clientLists.isEmpty()) {
            return result;
        }

        for (ShoppingListDto listDto : clientLists) {
            try {
                // Handle lists with or without syncId
                if (listDto.getSyncId() != null && !listDto.getSyncId().isEmpty()) {
                    processExistingList(listDto, user, syncTime, result);
                } else {
                    // No syncId provided, generate one and create a new list
                    createNewList(listDto, user, syncTime, result);
                }
            } catch (Exception e) {
                // Log error but continue processing other lists
                logger.error("Error processing list {} with syncId {}: {}",
                        listDto.getName(), listDto.getSyncId(), e.getMessage());
            }
        }

        return result;
    }

    /**
     * Process a list that already has a syncId - either update an existing one or create new.
     */
    private void processExistingList(ShoppingListDto listDto, User user, LocalDateTime syncTime,
                                     List<ShoppingListDto> result) {
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
                    handleListConflict(existingList.get(), listDto, syncTime, result);
                }
            }
        } else {
            // List doesn't exist - create a new one with the provided syncId
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
                    handleListSaveConflict(e, listDto, user, syncTime, result);
                }
            } catch (Exception ex) {
                logger.error("Error creating new list with syncId {}: {}",
                        listDto.getSyncId(), ex.getMessage());
            }
        }
    }

    /**
     * Handle conflict when updating an existing list.
     */
    private void handleListConflict(ShoppingList list, ShoppingListDto listDto,
                                    LocalDateTime syncTime, List<ShoppingListDto> result) {
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

    /**
     * Handle conflict when saving fails due to concurrent operations.
     */
    private void handleListSaveConflict(Exception e, ShoppingListDto listDto, User user,
                                        LocalDateTime syncTime, List<ShoppingListDto> result) {
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
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates a new list with a generated syncId.
     */
    private void createNewList(ShoppingListDto listDto, User user, LocalDateTime syncTime,
                               List<ShoppingListDto> result) {
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

    /**
     * Retrieves shopping lists that have changed on the server since the last synchronization.
     */
    @Transactional(readOnly = true)
    public List<ShoppingListDto> getChangedListsFromServer(User user, LocalDateTime lastSync) {
        if (lastSync == null) {
            // If this is the first synchronization, return all lists
            return listRepository.findAllByUser(user).stream()
                    .map(listService::convertToDto)
                    .toList();
        }

        // Otherwise, only return lists modified since the last synchronization
        return listRepository.findByUserAndLastSyncedAfter(user, lastSync).stream()
                .map(listService::convertToDto)
                .toList();
    }
}