package com.grocerymanager.api.service.sync;

import com.grocerymanager.api.dto.DeletedItemDto;
import com.grocerymanager.api.model.ShoppingItem;
import com.grocerymanager.api.model.ShoppingList;
import com.grocerymanager.api.model.StoreLocation;
import com.grocerymanager.api.model.User;
import com.grocerymanager.api.repository.ShoppingItemRepository;
import com.grocerymanager.api.repository.ShoppingListRepository;
import com.grocerymanager.api.repository.StoreLocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DeletedItemSyncServiceTest {

    @Mock
    private ShoppingItemRepository itemRepository;

    @Mock
    private ShoppingListRepository listRepository;

    @Mock
    private StoreLocationRepository storeRepository;

    @InjectMocks
    private DeletedItemSyncService deletedItemSyncService;

    private User testUser;
    private ShoppingItem testItem;
    private ShoppingList testList;
    private StoreLocation testStore;
    private List<DeletedItemDto> testDeletedItems;

    @BeforeEach
    void setUp() {
        // Set up test user
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");

        // Set up test shopping list
        testList = new ShoppingList();
        testList.setId(1L);
        testList.setName("Test Shopping List");
        testList.setSyncId("test-list-sync-id");
        testList.setUser(testUser);

        // Set up test shopping item
        testItem = new ShoppingItem();
        testItem.setId(1L);
        testItem.setName("Test Item");
        testItem.setSyncId("test-item-sync-id");
        testItem.setShoppingList(testList);

        // Set up test store location
        testStore = new StoreLocation();
        testStore.setId(1L);
        testStore.setName("Test Store");
        testStore.setSyncId("test-store-sync-id");
        testStore.setUser(testUser);

        // Set up test deleted items
        DeletedItemDto deletedItem1 = new DeletedItemDto();
        deletedItem1.setEntityType("SHOPPING_ITEM");
        deletedItem1.setSyncId("test-item-sync-id");
        deletedItem1.setDeletedAt(LocalDateTime.now());

        DeletedItemDto deletedItem2 = new DeletedItemDto();
        deletedItem2.setEntityType("SHOPPING_LIST");
        deletedItem2.setSyncId("test-list-sync-id");
        deletedItem2.setDeletedAt(LocalDateTime.now());

        DeletedItemDto deletedItem3 = new DeletedItemDto();
        deletedItem3.setEntityType("STORE_LOCATION");
        deletedItem3.setSyncId("test-store-sync-id");
        deletedItem3.setDeletedAt(LocalDateTime.now());

        testDeletedItems = Arrays.asList(deletedItem1, deletedItem2, deletedItem3);
    }

    @Test
    void processDeletedItems_ShouldDeleteAllItemTypes() {
        // Arrange
        when(itemRepository.findBySyncId("test-item-sync-id")).thenReturn(Optional.of(testItem));
        when(listRepository.findBySyncIdAndUser("test-list-sync-id", testUser)).thenReturn(Optional.of(testList));
        when(storeRepository.findBySyncIdAndUser("test-store-sync-id", testUser)).thenReturn(Optional.of(testStore));

        // Act
        deletedItemSyncService.processDeletedItems(testDeletedItems, testUser);

        // Assert
        verify(itemRepository).delete(testItem);
        verify(listRepository).delete(testList);
        verify(storeRepository).delete(testStore);
    }

    @Test
    void processDeletedItems_WithEmptyList_ShouldDoNothing() {
        // Arrange
        List<DeletedItemDto> emptyList = new ArrayList<>();

        // Act
        deletedItemSyncService.processDeletedItems(emptyList, testUser);

        // Assert
        verify(itemRepository, never()).findBySyncId(anyString());
        verify(listRepository, never()).findBySyncIdAndUser(anyString(), any(User.class));
        verify(storeRepository, never()).findBySyncIdAndUser(anyString(), any(User.class));
        verify(itemRepository, never()).delete(any(ShoppingItem.class));
        verify(listRepository, never()).delete(any(ShoppingList.class));
        verify(storeRepository, never()).delete(any(StoreLocation.class));
    }

    @Test
    void processDeletedItems_WithNullList_ShouldDoNothing() {
        // Act
        deletedItemSyncService.processDeletedItems(null, testUser);

        // Assert
        verify(itemRepository, never()).findBySyncId(anyString());
        verify(listRepository, never()).findBySyncIdAndUser(anyString(), any(User.class));
        verify(storeRepository, never()).findBySyncIdAndUser(anyString(), any(User.class));
        verify(itemRepository, never()).delete(any(ShoppingItem.class));
        verify(listRepository, never()).delete(any(ShoppingList.class));
        verify(storeRepository, never()).delete(any(StoreLocation.class));
    }

    @Test
    void processDeletedItems_WithNullSyncId_ShouldSkipItem() {
        // Arrange
        DeletedItemDto deletedItem = new DeletedItemDto();
        deletedItem.setEntityType("SHOPPING_ITEM");
        deletedItem.setSyncId(null);
        deletedItem.setDeletedAt(LocalDateTime.now());

        List<DeletedItemDto> itemsWithNullSyncId = Arrays.asList(deletedItem);

        // Act
        deletedItemSyncService.processDeletedItems(itemsWithNullSyncId, testUser);

        // Assert
        verify(itemRepository, never()).findBySyncId(anyString());
        verify(itemRepository, never()).delete(any(ShoppingItem.class));
    }

    @Test
    void processDeletedItems_WithUnknownEntityType_ShouldSkipItem() {
        // Arrange
        DeletedItemDto deletedItem = new DeletedItemDto();
        deletedItem.setEntityType("UNKNOWN_TYPE");
        deletedItem.setSyncId("test-sync-id");
        deletedItem.setDeletedAt(LocalDateTime.now());

        List<DeletedItemDto> itemsWithUnknownType = Arrays.asList(deletedItem);

        // Act
        deletedItemSyncService.processDeletedItems(itemsWithUnknownType, testUser);

        // Assert
        verify(itemRepository, never()).findBySyncId(anyString());
        verify(listRepository, never()).findBySyncIdAndUser(anyString(), any(User.class));
        verify(storeRepository, never()).findBySyncIdAndUser(anyString(), any(User.class));
        verify(itemRepository, never()).delete(any(ShoppingItem.class));
        verify(listRepository, never()).delete(any(ShoppingList.class));
        verify(storeRepository, never()).delete(any(StoreLocation.class));
    }

    @Test
    void processDeletedItems_WhenItemBelongsToAnotherUser_ShouldNotDelete() {
        // Arrange
        User anotherUser = new User();
        anotherUser.setId(2L);
        anotherUser.setUsername("anotheruser");

        ShoppingList anotherUserList = new ShoppingList();
        anotherUserList.setId(2L);
        anotherUserList.setUser(anotherUser);

        ShoppingItem itemBelongingToAnotherUser = new ShoppingItem();
        itemBelongingToAnotherUser.setId(2L);
        itemBelongingToAnotherUser.setSyncId("another-item-sync-id");
        itemBelongingToAnotherUser.setShoppingList(anotherUserList);

        DeletedItemDto deletedItem = new DeletedItemDto();
        deletedItem.setEntityType("SHOPPING_ITEM");
        deletedItem.setSyncId("another-item-sync-id");

        when(itemRepository.findBySyncId("another-item-sync-id")).thenReturn(Optional.of(itemBelongingToAnotherUser));

        // Act
        deletedItemSyncService.processDeletedItems(Arrays.asList(deletedItem), testUser);

        // Assert
        verify(itemRepository, never()).delete(any(ShoppingItem.class));
    }

    @Test
    void processDeletedItems_WhenRepositoryThrowsException_ShouldContinueWithOtherItems() {
        // Arrange
        when(itemRepository.findBySyncId("test-item-sync-id")).thenThrow(new RuntimeException("Test exception"));
        when(listRepository.findBySyncIdAndUser("test-list-sync-id", testUser)).thenReturn(Optional.of(testList));
        when(storeRepository.findBySyncIdAndUser("test-store-sync-id", testUser)).thenReturn(Optional.of(testStore));

        // Act
        deletedItemSyncService.processDeletedItems(testDeletedItems, testUser);

        // Assert
        verify(itemRepository, never()).delete(any(ShoppingItem.class));
        verify(listRepository).delete(testList);
        verify(storeRepository).delete(testStore);
    }
}