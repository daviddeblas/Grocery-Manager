package com.grocerymanager.api.service.sync;

import com.grocerymanager.api.dto.ShoppingItemDto;
import com.grocerymanager.api.model.ShoppingItem;
import com.grocerymanager.api.model.ShoppingList;
import com.grocerymanager.api.model.User;
import com.grocerymanager.api.repository.ShoppingItemRepository;
import com.grocerymanager.api.repository.ShoppingListRepository;
import com.grocerymanager.api.service.ShoppingItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ShoppingItemSyncServiceTest {

    @Mock
    private ShoppingItemRepository itemRepository;

    @Mock
    private ShoppingListRepository listRepository;

    @Mock
    private ShoppingItemService itemService;

    @InjectMocks
    private ShoppingItemSyncService itemSyncService;

    private User testUser;
    private ShoppingList testList;
    private ShoppingItem testItem;
    private ShoppingItemDto testItemDto;
    private LocalDateTime testSyncTime;

    @BeforeEach
    void setUp() {
        // Set up test user
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");

        // Set up test sync time
        testSyncTime = LocalDateTime.now();

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
        testItem.setQuantity(2.0);
        testItem.setUnitType("kg");
        testItem.setChecked(false);
        testItem.setSortIndex(0);
        testItem.setSyncId("test-item-sync-id");
        testItem.setShoppingList(testList);
        testItem.setCreatedAt(testSyncTime.minusDays(1));
        testItem.setUpdatedAt(testSyncTime.minusDays(1));

        // Set up test shopping item DTO
        testItemDto = new ShoppingItemDto();
        testItemDto.setId(1L);
        testItemDto.setName("Test Item");
        testItemDto.setQuantity(2.0);
        testItemDto.setUnitType("kg");
        testItemDto.setChecked(false);
        testItemDto.setSortIndex(0);
        testItemDto.setShoppingListId(1L);
        testItemDto.setSyncId("test-item-sync-id");
        testItemDto.setCreatedAt(testSyncTime.minusDays(1));
        testItemDto.setUpdatedAt(testSyncTime.minusDays(1));

        // Set up mock for convertToDto with lenient stubbing
        Mockito.lenient().when(itemService.convertToDto(any(ShoppingItem.class))).thenAnswer(invocation -> {
            ShoppingItem item = invocation.getArgument(0);
            ShoppingItemDto dto = new ShoppingItemDto();
            dto.setId(item.getId());
            dto.setName(item.getName());
            dto.setQuantity(item.getQuantity());
            dto.setUnitType(item.getUnitType());
            dto.setChecked(item.isChecked());
            dto.setSortIndex(item.getSortIndex());
            dto.setSyncId(item.getSyncId());
            dto.setShoppingListId(item.getShoppingList().getId());
            dto.setCreatedAt(item.getCreatedAt());
            dto.setUpdatedAt(item.getUpdatedAt());
            return dto;
        });
    }

    @Test
    void syncShoppingItemsInNewTransaction_ShouldDelegateToSyncShoppingItems() {
        // Arrange
        List<ShoppingItemDto> clientItems = Arrays.asList(testItemDto);
        List<ShoppingItemDto> expectedResult = Arrays.asList(testItemDto);

        // Create a spy on the service to verify the delegation
        ShoppingItemSyncService spyService = spy(itemSyncService);
        doReturn(expectedResult).when(spyService).syncShoppingItems(clientItems, testUser, testSyncTime);

        // Act
        List<ShoppingItemDto> result = spyService.syncShoppingItemsInNewTransaction(clientItems, testUser, testSyncTime);

        // Assert
        verify(spyService).syncShoppingItems(clientItems, testUser, testSyncTime);
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    void syncShoppingItems_WithEmptyList_ShouldReturnEmptyList() {
        // Arrange
        List<ShoppingItemDto> emptyList = new ArrayList<>();

        // Act
        List<ShoppingItemDto> result = itemSyncService.syncShoppingItems(emptyList, testUser, testSyncTime);

        // Assert
        assertThat(result).isEmpty();
        verify(listRepository, never()).findByIdAndUser(anyLong(), any(User.class));
        verify(itemRepository, never()).safeUpsertItem(anyString(), anyDouble(), anyString(), anyBoolean(), anyInt(), anyLong(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(itemRepository, never()).findBySyncId(anyString());
        verify(itemRepository, never()).save(any(ShoppingItem.class));
    }

    @Test
    void syncShoppingItems_WithNullList_ShouldReturnEmptyList() {
        // Act
        List<ShoppingItemDto> result = itemSyncService.syncShoppingItems(null, testUser, testSyncTime);

        // Assert
        assertThat(result).isEmpty();
        verify(listRepository, never()).findByIdAndUser(anyLong(), any(User.class));
        verify(itemRepository, never()).safeUpsertItem(anyString(), anyDouble(), anyString(), anyBoolean(), anyInt(), anyLong(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(itemRepository, never()).findBySyncId(anyString());
        verify(itemRepository, never()).save(any(ShoppingItem.class));
    }

    @Test
    void syncShoppingItems_WhenListNotFound_ShouldSkipItem() {
        // Arrange
        List<ShoppingItemDto> clientItems = Arrays.asList(testItemDto);
        when(listRepository.findByIdAndUser(1L, testUser)).thenReturn(Optional.empty());

        // Act
        List<ShoppingItemDto> result = itemSyncService.syncShoppingItems(clientItems, testUser, testSyncTime);

        // Assert
        assertThat(result).isEmpty();
        verify(listRepository).findByIdAndUser(1L, testUser);
        verify(itemRepository, never()).safeUpsertItem(anyString(), anyDouble(), anyString(), anyBoolean(), anyInt(), anyLong(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(itemRepository, never()).findBySyncId(anyString());
        verify(itemRepository, never()).save(any(ShoppingItem.class));
    }

    @Test
    void syncShoppingItems_WithUpsertSuccess_ShouldReturnUpdatedItem() throws InterruptedException {
        // Arrange
        List<ShoppingItemDto> clientItems = Arrays.asList(testItemDto);
        when(listRepository.findByIdAndUser(1L, testUser)).thenReturn(Optional.of(testList));
        when(itemRepository.safeUpsertItem(
                eq("Test Item"), eq(2.0), eq("kg"), eq(false), eq(0), eq(1L),
                eq("test-item-sync-id"), any(LocalDateTime.class), any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(1L);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(testItem));

        // Act
        List<ShoppingItemDto> result = itemSyncService.syncShoppingItems(clientItems, testUser, testSyncTime);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSyncId()).isEqualTo("test-item-sync-id");
        verify(listRepository).findByIdAndUser(1L, testUser);
        verify(itemRepository).safeUpsertItem(
                eq("Test Item"), eq(2.0), eq("kg"), eq(false), eq(0), eq(1L),
                eq("test-item-sync-id"), any(LocalDateTime.class), any(LocalDateTime.class), any(LocalDateTime.class)
        );
        verify(itemRepository).findById(1L);
        verify(itemService).convertToDto(testItem);
    }

    @Test
    void syncShoppingItems_WithUpsertFailure_ShouldTryTraditionalApproach() throws InterruptedException {
        // Arrange
        List<ShoppingItemDto> clientItems = Arrays.asList(testItemDto);
        when(listRepository.findByIdAndUser(1L, testUser)).thenReturn(Optional.of(testList));

        // Upsert fails
        when(itemRepository.safeUpsertItem(
                anyString(), anyDouble(), anyString(), anyBoolean(), anyInt(), anyLong(),
                anyString(), any(LocalDateTime.class), any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenThrow(new RuntimeException("Upsert failed"));

        // Traditional approach succeeds
        when(itemRepository.findBySyncId("test-item-sync-id")).thenReturn(Optional.of(testItem));
        when(itemRepository.save(any(ShoppingItem.class))).thenReturn(testItem);

        // Act
        List<ShoppingItemDto> result = itemSyncService.syncShoppingItems(clientItems, testUser, testSyncTime);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSyncId()).isEqualTo("test-item-sync-id");
        verify(listRepository).findByIdAndUser(1L, testUser);
        verify(itemRepository).safeUpsertItem(
                anyString(), anyDouble(), anyString(), anyBoolean(), anyInt(), anyLong(),
                anyString(), any(LocalDateTime.class), any(LocalDateTime.class), any(LocalDateTime.class)
        );
        verify(itemRepository).findBySyncId("test-item-sync-id");
        verify(itemRepository).save(any(ShoppingItem.class));
        verify(itemService).convertToDto(testItem);
    }

    @Test
    void syncShoppingItems_WithNewItemWithoutSyncId_ShouldCreateNewItem() throws InterruptedException {
        // Arrange
        ShoppingItemDto newItemDto = new ShoppingItemDto();
        newItemDto.setName("New Item");
        newItemDto.setQuantity(1.0);
        newItemDto.setUnitType("units");
        newItemDto.setChecked(false);
        newItemDto.setSortIndex(1);
        newItemDto.setShoppingListId(1L);
        // No syncId

        List<ShoppingItemDto> clientItems = Arrays.asList(newItemDto);
        when(listRepository.findByIdAndUser(1L, testUser)).thenReturn(Optional.of(testList));

        ShoppingItem savedItem = new ShoppingItem();
        savedItem.setId(2L);
        savedItem.setName("New Item");
        savedItem.setQuantity(1.0);
        savedItem.setUnitType("units");
        savedItem.setChecked(false);
        savedItem.setSortIndex(1);
        savedItem.setShoppingList(testList);
        savedItem.setSyncId("generated-sync-id");
        savedItem.setCreatedAt(testSyncTime);
        savedItem.setUpdatedAt(testSyncTime);

        when(itemRepository.save(any(ShoppingItem.class))).thenReturn(savedItem);

        // Act
        List<ShoppingItemDto> result = itemSyncService.syncShoppingItems(clientItems, testUser, testSyncTime);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("New Item");
        verify(listRepository).findByIdAndUser(1L, testUser);
        verify(itemRepository, never()).safeUpsertItem(
                anyString(), anyDouble(), anyString(), anyBoolean(), anyInt(), anyLong(),
                anyString(), any(LocalDateTime.class), any(LocalDateTime.class), any(LocalDateTime.class)
        );
        verify(itemRepository, never()).findBySyncId(anyString());
        verify(itemRepository).save(any(ShoppingItem.class));
        verify(itemService).convertToDto(savedItem);
    }

    @Test
    void syncShoppingItems_WhenRepositoryThrowsException_ShouldContinueWithOtherItems() throws InterruptedException {
        // Arrange
        ShoppingItemDto item1 = new ShoppingItemDto();
        item1.setName("Item 1");
        item1.setQuantity(1.0);
        item1.setUnitType("units");
        item1.setShoppingListId(1L);
        item1.setSyncId("item1-sync-id");

        ShoppingItemDto item2 = new ShoppingItemDto();
        item2.setName("Item 2");
        item2.setQuantity(2.0);
        item2.setUnitType("kg");
        item2.setShoppingListId(1L);
        item2.setSyncId("item2-sync-id");

        List<ShoppingItemDto> clientItems = Arrays.asList(item1, item2);

        when(listRepository.findByIdAndUser(1L, testUser)).thenReturn(Optional.of(testList));

        // First item processing throws exception
        when(itemRepository.safeUpsertItem(
                eq("Item 1"), eq(1.0), eq("units"), anyBoolean(), anyInt(), eq(1L),
                eq("item1-sync-id"), any(LocalDateTime.class), any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenThrow(new RuntimeException("Test exception"));
        when(itemRepository.findBySyncId("item1-sync-id")).thenThrow(new RuntimeException("Test exception"));

        // Second item processes successfully
        when(itemRepository.safeUpsertItem(
                eq("Item 2"), eq(2.0), eq("kg"), anyBoolean(), anyInt(), eq(1L),
                eq("item2-sync-id"), any(LocalDateTime.class), any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(2L);

        ShoppingItem savedItem2 = new ShoppingItem();
        savedItem2.setId(2L);
        savedItem2.setName("Item 2");
        savedItem2.setQuantity(2.0);
        savedItem2.setUnitType("kg");
        savedItem2.setShoppingList(testList);
        savedItem2.setSyncId("item2-sync-id");

        when(itemRepository.findById(2L)).thenReturn(Optional.of(savedItem2));

        // Act
        List<ShoppingItemDto> result = itemSyncService.syncShoppingItems(clientItems, testUser, testSyncTime);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSyncId()).isEqualTo("item2-sync-id");
        verify(itemRepository).safeUpsertItem(
                eq("Item 1"), eq(1.0), eq("units"), anyBoolean(), anyInt(), eq(1L),
                eq("item1-sync-id"), any(LocalDateTime.class), any(LocalDateTime.class), any(LocalDateTime.class)
        );
        verify(itemRepository).safeUpsertItem(
                eq("Item 2"), eq(2.0), eq("kg"), anyBoolean(), anyInt(), eq(1L),
                eq("item2-sync-id"), any(LocalDateTime.class), any(LocalDateTime.class), any(LocalDateTime.class)
        );
        verify(itemRepository).findById(2L);
        verify(itemService).convertToDto(savedItem2);
    }

    @Test
    void getChangedItemsFromServer_WithLastSyncNull_ShouldReturnAllItems() {
        // Arrange
        List<ShoppingList> userLists = Arrays.asList(testList);
        List<ShoppingItem> allItems = Arrays.asList(testItem);

        when(listRepository.findAllByUser(testUser)).thenReturn(userLists);
        when(itemRepository.findAllByShoppingList(testList)).thenReturn(allItems);

        // Act
        List<ShoppingItemDto> result = itemSyncService.getChangedItemsFromServer(testUser, null);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSyncId()).isEqualTo("test-item-sync-id");
        verify(listRepository).findAllByUser(testUser);
        verify(itemRepository).findAllByShoppingList(testList);
        verify(itemRepository, never()).findByShoppingListAndLastSyncedAfter(any(ShoppingList.class), any(LocalDateTime.class));
        verify(itemService).convertToDto(testItem);
    }

    @Test
    void getChangedItemsFromServer_WithLastSync_ShouldReturnOnlyChangedItems() {
        // Arrange
        LocalDateTime lastSync = testSyncTime.minusHours(1);
        List<ShoppingList> userLists = Arrays.asList(testList);
        List<ShoppingItem> changedItems = Arrays.asList(testItem);

        when(listRepository.findAllByUser(testUser)).thenReturn(userLists);
        when(itemRepository.findByShoppingListAndLastSyncedAfter(testList, lastSync)).thenReturn(changedItems);

        // Act
        List<ShoppingItemDto> result = itemSyncService.getChangedItemsFromServer(testUser, lastSync);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSyncId()).isEqualTo("test-item-sync-id");
        verify(listRepository).findAllByUser(testUser);
        verify(itemRepository, never()).findAllByShoppingList(any(ShoppingList.class));
        verify(itemRepository).findByShoppingListAndLastSyncedAfter(testList, lastSync);
        verify(itemService).convertToDto(testItem);
    }
}
