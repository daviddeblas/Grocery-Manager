package com.grocerymanager.api.service;

import com.grocerymanager.api.dto.ShoppingItemDto;
import com.grocerymanager.api.dto.ShoppingListDto;
import com.grocerymanager.api.dto.StoreLocationDto;
import com.grocerymanager.api.dto.SyncRequest;
import com.grocerymanager.api.dto.SyncResponse;
import com.grocerymanager.api.dto.DeletedItemDto;
import com.grocerymanager.api.model.User;
import com.grocerymanager.api.service.sync.SyncService;
import com.grocerymanager.api.service.sync.ShoppingListSyncService;
import com.grocerymanager.api.service.sync.ShoppingItemSyncService;
import com.grocerymanager.api.service.sync.StoreLocationSyncService;
import com.grocerymanager.api.service.sync.DeletedItemSyncService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SyncServiceTest {

    @Mock
    private ShoppingListSyncService listSyncService;

    @Mock
    private ShoppingItemSyncService itemSyncService;

    @Mock
    private StoreLocationSyncService storeSyncService;

    @Mock
    private DeletedItemSyncService deletedItemSyncService;

    @InjectMocks
    private SyncService syncService;

    private User testUser;
    private SyncRequest testRequest;
    private List<ShoppingListDto> testListDtos;
    private List<ShoppingItemDto> testItemDtos;
    private List<StoreLocationDto> testStoreDtos;
    private List<DeletedItemDto> testDeletedItems;
    private LocalDateTime testLastSync;

    @BeforeEach
    void setUp() {
        // Set up test user
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");

        // Set up last sync time
        testLastSync = LocalDateTime.now().minusDays(1);

        // Set up test lists
        ShoppingListDto list = new ShoppingListDto();
        list.setId(1L);
        list.setName("Test Shopping List");
        list.setSyncId("test-list-sync-id");
        list.setCreatedAt(LocalDateTime.now());
        list.setUpdatedAt(LocalDateTime.now());
        testListDtos = Arrays.asList(list);

        // Set up test items
        ShoppingItemDto item = new ShoppingItemDto();
        item.setId(1L);
        item.setName("Test Item");
        item.setQuantity(2.0);
        item.setUnitType("kg");
        item.setChecked(false);
        item.setSortIndex(0);
        item.setShoppingListId(1L);
        item.setSyncId("test-item-sync-id");
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        testItemDtos = Arrays.asList(item);

        // Set up test stores
        StoreLocationDto store = new StoreLocationDto();
        store.setId(1L);
        store.setName("Test Store");
        store.setAddress("123 Test Street");
        store.setLatitude(40.7128);
        store.setLongitude(-74.0060);
        store.setGeofenceId("test-geofence-id");
        store.setSyncId("test-store-sync-id");
        store.setCreatedAt(LocalDateTime.now());
        store.setUpdatedAt(LocalDateTime.now());
        testStoreDtos = Arrays.asList(store);

        // Set up test deleted items
        DeletedItemDto deletedItem = new DeletedItemDto();
        deletedItem.setEntityType("SHOPPING_ITEM");
        deletedItem.setSyncId("deleted-item-sync-id");
        deletedItem.setDeletedAt(LocalDateTime.now());
        testDeletedItems = Arrays.asList(deletedItem);

        // Set up test request
        testRequest = new SyncRequest();
        testRequest.setLastSyncTimestamp(testLastSync);
        testRequest.setShoppingLists(testListDtos);
        testRequest.setShoppingItems(testItemDtos);
        testRequest.setStoreLocations(testStoreDtos);
        testRequest.setDeletedItems(testDeletedItems);
    }

    @Test
    void synchronize_ShouldProcessAllEntitiesAndReturnMergedResponse() {
        // Arrange
        // Mock deleted items processing
        doNothing().when(deletedItemSyncService).processDeletedItems(anyList(), any(User.class));

        // Mock list synchronization
        List<ShoppingListDto> syncedLists = new ArrayList<>(testListDtos);
        ShoppingListDto serverList = new ShoppingListDto();
        serverList.setId(2L);
        serverList.setName("Server Shopping List");
        serverList.setSyncId("server-list-sync-id");
        syncedLists.add(serverList);

        when(listSyncService.syncShoppingListsInNewTransaction(anyList(), any(User.class), any(LocalDateTime.class)))
                .thenReturn(testListDtos);
        when(listSyncService.getChangedListsFromServer(any(User.class), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(serverList));

        // Mock item synchronization
        List<ShoppingItemDto> syncedItems = new ArrayList<>(testItemDtos);
        ShoppingItemDto serverItem = new ShoppingItemDto();
        serverItem.setId(2L);
        serverItem.setName("Server Item");
        serverItem.setQuantity(1.0);
        serverItem.setUnitType("units");
        serverItem.setSyncId("server-item-sync-id");
        syncedItems.add(serverItem);

        when(itemSyncService.syncShoppingItemsInNewTransaction(anyList(), any(User.class), any(LocalDateTime.class)))
                .thenReturn(testItemDtos);
        when(itemSyncService.getChangedItemsFromServer(any(User.class), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(serverItem));

        // Mock store synchronization
        List<StoreLocationDto> syncedStores = new ArrayList<>(testStoreDtos);
        StoreLocationDto serverStore = new StoreLocationDto();
        serverStore.setId(2L);
        serverStore.setName("Server Store");
        serverStore.setAddress("456 Server Street");
        serverStore.setSyncId("server-store-sync-id");
        syncedStores.add(serverStore);

        when(storeSyncService.syncStoreLocationsInNewTransaction(anyList(), any(User.class), any(LocalDateTime.class)))
                .thenReturn(testStoreDtos);
        when(storeSyncService.getChangedStoresFromServer(any(User.class), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(serverStore));

        // Act
        SyncResponse response = syncService.synchronize(testRequest, testUser);

        // Assert
        // Verify all synchronization methods were called
        verify(deletedItemSyncService).processDeletedItems(eq(testDeletedItems), eq(testUser));
        verify(listSyncService).syncShoppingListsInNewTransaction(eq(testListDtos), eq(testUser), any(LocalDateTime.class));
        verify(itemSyncService).syncShoppingItemsInNewTransaction(eq(testItemDtos), eq(testUser), any(LocalDateTime.class));
        verify(storeSyncService).syncStoreLocationsInNewTransaction(eq(testStoreDtos), eq(testUser), any(LocalDateTime.class));

        // Verify server changes were retrieved
        verify(listSyncService).getChangedListsFromServer(eq(testUser), eq(testLastSync));
        verify(itemSyncService).getChangedItemsFromServer(eq(testUser), eq(testLastSync));
        verify(storeSyncService).getChangedStoresFromServer(eq(testUser), eq(testLastSync));

        // Verify response contains both client and server entities
        assertThat(response).isNotNull();
        assertThat(response.getServerTimestamp()).isNotNull();

        // Check that we have both client and server lists
        assertThat(response.getShoppingLists()).hasSize(2);
        assertThat(response.getShoppingLists().stream().map(ShoppingListDto::getSyncId))
                .contains("test-list-sync-id", "server-list-sync-id");

        // Check that we have both client and server items
        assertThat(response.getShoppingItems()).hasSize(2);
        assertThat(response.getShoppingItems().stream().map(ShoppingItemDto::getSyncId))
                .contains("test-item-sync-id", "server-item-sync-id");

        // Check that we have both client and server stores
        assertThat(response.getStoreLocations()).hasSize(2);
        assertThat(response.getStoreLocations().stream().map(StoreLocationDto::getSyncId))
                .contains("test-store-sync-id", "server-store-sync-id");
    }

    @Test
    void synchronize_WhenDeletedItemProcessingFails_ShouldContinueWithOtherSync() {
        // Arrange
        // Mock deleted items processing to throw exception
        doThrow(new RuntimeException("Test exception")).when(deletedItemSyncService)
                .processDeletedItems(anyList(), any(User.class));

        // Other mocks same as successful case
        when(listSyncService.syncShoppingListsInNewTransaction(anyList(), any(User.class), any(LocalDateTime.class)))
                .thenReturn(testListDtos);
        when(listSyncService.getChangedListsFromServer(any(User.class), any(LocalDateTime.class)))
                .thenReturn(new ArrayList<>());

        when(itemSyncService.syncShoppingItemsInNewTransaction(anyList(), any(User.class), any(LocalDateTime.class)))
                .thenReturn(testItemDtos);
        when(itemSyncService.getChangedItemsFromServer(any(User.class), any(LocalDateTime.class)))
                .thenReturn(new ArrayList<>());

        when(storeSyncService.syncStoreLocationsInNewTransaction(anyList(), any(User.class), any(LocalDateTime.class)))
                .thenReturn(testStoreDtos);
        when(storeSyncService.getChangedStoresFromServer(any(User.class), any(LocalDateTime.class)))
                .thenReturn(new ArrayList<>());

        // Act
        SyncResponse response = syncService.synchronize(testRequest, testUser);

        // Assert
        // Verify delete processing was attempted
        verify(deletedItemSyncService).processDeletedItems(eq(testDeletedItems), eq(testUser));

        // Verify other sync methods were still called despite the failure
        verify(listSyncService).syncShoppingListsInNewTransaction(eq(testListDtos), eq(testUser), any(LocalDateTime.class));
        verify(itemSyncService).syncShoppingItemsInNewTransaction(eq(testItemDtos), eq(testUser), any(LocalDateTime.class));
        verify(storeSyncService).syncStoreLocationsInNewTransaction(eq(testStoreDtos), eq(testUser), any(LocalDateTime.class));

        // Verify response still contains all other entities
        assertThat(response).isNotNull();
        assertThat(response.getServerTimestamp()).isNotNull();
        assertThat(response.getShoppingLists()).hasSize(1);
        assertThat(response.getShoppingItems()).hasSize(1);
        assertThat(response.getStoreLocations()).hasSize(1);
    }

    @Test
    void synchronize_WhenListSyncFails_ShouldContinueWithOtherSyncs() {
        // Arrange
        // Mock deleted items processing
        doNothing().when(deletedItemSyncService).processDeletedItems(anyList(), any(User.class));

        // Mock list sync to throw exception
        when(listSyncService.syncShoppingListsInNewTransaction(anyList(), any(User.class), any(LocalDateTime.class)))
                .thenThrow(new RuntimeException("Test exception"));
        when(listSyncService.getChangedListsFromServer(any(User.class), any(LocalDateTime.class)))
                .thenReturn(new ArrayList<>());

        // Other mocks same as successful case
        when(itemSyncService.syncShoppingItemsInNewTransaction(anyList(), any(User.class), any(LocalDateTime.class)))
                .thenReturn(testItemDtos);
        when(itemSyncService.getChangedItemsFromServer(any(User.class), any(LocalDateTime.class)))
                .thenReturn(new ArrayList<>());

        when(storeSyncService.syncStoreLocationsInNewTransaction(anyList(), any(User.class), any(LocalDateTime.class)))
                .thenReturn(testStoreDtos);
        when(storeSyncService.getChangedStoresFromServer(any(User.class), any(LocalDateTime.class)))
                .thenReturn(new ArrayList<>());

        // Act
        SyncResponse response = syncService.synchronize(testRequest, testUser);

        // Assert
        // Verify sync methods were called
        verify(deletedItemSyncService).processDeletedItems(eq(testDeletedItems), eq(testUser));
        verify(listSyncService).syncShoppingListsInNewTransaction(eq(testListDtos), eq(testUser), any(LocalDateTime.class));

        // Verify other sync methods were still called despite the list sync failure
        verify(itemSyncService).syncShoppingItemsInNewTransaction(eq(testItemDtos), eq(testUser), any(LocalDateTime.class));
        verify(storeSyncService).syncStoreLocationsInNewTransaction(eq(testStoreDtos), eq(testUser), any(LocalDateTime.class));

        // Verify response contains items and stores but not lists
        assertThat(response).isNotNull();
        assertThat(response.getServerTimestamp()).isNotNull();
        assertThat(response.getShoppingLists()).isEmpty();
        assertThat(response.getShoppingItems()).hasSize(1);
        assertThat(response.getStoreLocations()).hasSize(1);
    }

    @Test
    void synchronize_WhenAllGetChangedMethodsFail_ShouldReturnOnlyClientChanges() {
        // Arrange
        // Mock deleted items processing
        doNothing().when(deletedItemSyncService).processDeletedItems(anyList(), any(User.class));

        // Mock client-side sync success
        when(listSyncService.syncShoppingListsInNewTransaction(anyList(), any(User.class), any(LocalDateTime.class)))
                .thenReturn(testListDtos);
        when(itemSyncService.syncShoppingItemsInNewTransaction(anyList(), any(User.class), any(LocalDateTime.class)))
                .thenReturn(testItemDtos);
        when(storeSyncService.syncStoreLocationsInNewTransaction(anyList(), any(User.class), any(LocalDateTime.class)))
                .thenReturn(testStoreDtos);

        // Mock server-side sync failures
        when(listSyncService.getChangedListsFromServer(any(User.class), any(LocalDateTime.class)))
                .thenThrow(new RuntimeException("Test exception"));
        when(itemSyncService.getChangedItemsFromServer(any(User.class), any(LocalDateTime.class)))
                .thenThrow(new RuntimeException("Test exception"));
        when(storeSyncService.getChangedStoresFromServer(any(User.class), any(LocalDateTime.class)))
                .thenThrow(new RuntimeException("Test exception"));

        // Act
        SyncResponse response = syncService.synchronize(testRequest, testUser);

        // Assert
        // Verify client-side sync methods were called
        verify(deletedItemSyncService).processDeletedItems(eq(testDeletedItems), eq(testUser));
        verify(listSyncService).syncShoppingListsInNewTransaction(eq(testListDtos), eq(testUser), any(LocalDateTime.class));
        verify(itemSyncService).syncShoppingItemsInNewTransaction(eq(testItemDtos), eq(testUser), any(LocalDateTime.class));
        verify(storeSyncService).syncStoreLocationsInNewTransaction(eq(testStoreDtos), eq(testUser), any(LocalDateTime.class));

        // Verify server-side sync methods were attempted
        verify(listSyncService).getChangedListsFromServer(eq(testUser), eq(testLastSync));
        verify(itemSyncService).getChangedItemsFromServer(eq(testUser), eq(testLastSync));
        verify(storeSyncService).getChangedStoresFromServer(eq(testUser), eq(testLastSync));

        // Verify response contains only client changes
        assertThat(response).isNotNull();
        assertThat(response.getServerTimestamp()).isNotNull();
        assertThat(response.getShoppingLists()).hasSize(1);
        assertThat(response.getShoppingItems()).hasSize(1);
        assertThat(response.getStoreLocations()).hasSize(1);

        // Verify they are specifically the client items
        assertThat(response.getShoppingLists().get(0).getSyncId()).isEqualTo("test-list-sync-id");
        assertThat(response.getShoppingItems().get(0).getSyncId()).isEqualTo("test-item-sync-id");
        assertThat(response.getStoreLocations().get(0).getSyncId()).isEqualTo("test-store-sync-id");
    }

    @Test
    void synchronize_WithEmptyRequest_ShouldReturnOnlyServerChanges() {
        // Arrange
        // Create empty request
        SyncRequest emptyRequest = new SyncRequest();
        emptyRequest.setLastSyncTimestamp(testLastSync);
        emptyRequest.setShoppingLists(new ArrayList<>());
        emptyRequest.setShoppingItems(new ArrayList<>());
        emptyRequest.setStoreLocations(new ArrayList<>());
        emptyRequest.setDeletedItems(new ArrayList<>());

        // Mock server-side sync
        ShoppingListDto serverList = new ShoppingListDto();
        serverList.setId(2L);
        serverList.setName("Server Shopping List");
        serverList.setSyncId("server-list-sync-id");

        ShoppingItemDto serverItem = new ShoppingItemDto();
        serverItem.setId(2L);
        serverItem.setName("Server Item");
        serverItem.setSyncId("server-item-sync-id");

        StoreLocationDto serverStore = new StoreLocationDto();
        serverStore.setId(2L);
        serverStore.setName("Server Store");
        serverStore.setSyncId("server-store-sync-id");

        when(listSyncService.getChangedListsFromServer(any(User.class), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(serverList));
        when(itemSyncService.getChangedItemsFromServer(any(User.class), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(serverItem));
        when(storeSyncService.getChangedStoresFromServer(any(User.class), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(serverStore));

        // Act
        SyncResponse response = syncService.synchronize(emptyRequest, testUser);

        // Assert
        // Verify client-side sync methods were called with empty lists
        verify(deletedItemSyncService, never()).processDeletedItems(anyList(), any(User.class));
        verify(listSyncService, never()).syncShoppingListsInNewTransaction(anyList(), any(User.class), any(LocalDateTime.class));
        verify(itemSyncService, never()).syncShoppingItemsInNewTransaction(anyList(), any(User.class), any(LocalDateTime.class));
        verify(storeSyncService, never()).syncStoreLocationsInNewTransaction(anyList(), any(User.class), any(LocalDateTime.class));

        // Verify server-side sync methods were called
        verify(listSyncService).getChangedListsFromServer(eq(testUser), eq(testLastSync));
        verify(itemSyncService).getChangedItemsFromServer(eq(testUser), eq(testLastSync));
        verify(storeSyncService).getChangedStoresFromServer(eq(testUser), eq(testLastSync));

        // Verify response contains only server changes
        assertThat(response).isNotNull();
        assertThat(response.getServerTimestamp()).isNotNull();
        assertThat(response.getShoppingLists()).hasSize(1);
        assertThat(response.getShoppingItems()).hasSize(1);
        assertThat(response.getStoreLocations()).hasSize(1);

        // Verify they are specifically the server items
        assertThat(response.getShoppingLists().get(0).getSyncId()).isEqualTo("server-list-sync-id");
        assertThat(response.getShoppingItems().get(0).getSyncId()).isEqualTo("server-item-sync-id");
        assertThat(response.getStoreLocations().get(0).getSyncId()).isEqualTo("server-store-sync-id");
    }

    @Test
    void synchronize_WithFirstTimeSync_ShouldGetAllServerItems() {
        // Arrange
        // Create request with null lastSyncTimestamp (first sync)
        SyncRequest firstSyncRequest = new SyncRequest();
        firstSyncRequest.setLastSyncTimestamp(null);
        firstSyncRequest.setShoppingLists(new ArrayList<>());
        firstSyncRequest.setShoppingItems(new ArrayList<>());
        firstSyncRequest.setStoreLocations(new ArrayList<>());
        firstSyncRequest.setDeletedItems(new ArrayList<>());

        // Mock server-side sync to return ALL items
        when(listSyncService.getChangedListsFromServer(any(User.class), isNull()))
                .thenReturn(testListDtos);
        when(itemSyncService.getChangedItemsFromServer(any(User.class), isNull()))
                .thenReturn(testItemDtos);
        when(storeSyncService.getChangedStoresFromServer(any(User.class), isNull()))
                .thenReturn(testStoreDtos);

        // Act
        SyncResponse response = syncService.synchronize(firstSyncRequest, testUser);

        // Assert
        // Verify server-side sync methods were called with NULL timestamp
        verify(listSyncService).getChangedListsFromServer(eq(testUser), isNull());
        verify(itemSyncService).getChangedItemsFromServer(eq(testUser), isNull());
        verify(storeSyncService).getChangedStoresFromServer(eq(testUser), isNull());

        // Verify response contains all server items
        assertThat(response).isNotNull();
        assertThat(response.getServerTimestamp()).isNotNull();
        assertThat(response.getShoppingLists()).hasSize(1);
        assertThat(response.getShoppingItems()).hasSize(1);
        assertThat(response.getStoreLocations()).hasSize(1);
    }

    @Test
    void synchronize_WhenItemSyncFails_ShouldContinueWithOtherSyncs() {
        // Arrange
        // Mock deleted items processing
        doNothing().when(deletedItemSyncService).processDeletedItems(anyList(), any(User.class));

        // Mock list sync success
        when(listSyncService.syncShoppingListsInNewTransaction(anyList(), any(User.class), any(LocalDateTime.class)))
                .thenReturn(testListDtos);
        when(listSyncService.getChangedListsFromServer(any(User.class), any(LocalDateTime.class)))
                .thenReturn(new ArrayList<>());

        // Mock item sync to throw exception
        when(itemSyncService.syncShoppingItemsInNewTransaction(anyList(), any(User.class), any(LocalDateTime.class)))
                .thenThrow(new RuntimeException("Test exception"));
        when(itemSyncService.getChangedItemsFromServer(any(User.class), any(LocalDateTime.class)))
                .thenReturn(new ArrayList<>());

        // Mock store sync success
        when(storeSyncService.syncStoreLocationsInNewTransaction(anyList(), any(User.class), any(LocalDateTime.class)))
                .thenReturn(testStoreDtos);
        when(storeSyncService.getChangedStoresFromServer(any(User.class), any(LocalDateTime.class)))
                .thenReturn(new ArrayList<>());

        // Act
        SyncResponse response = syncService.synchronize(testRequest, testUser);

        // Assert
        // Verify sync methods were called
        verify(deletedItemSyncService).processDeletedItems(eq(testDeletedItems), eq(testUser));
        verify(listSyncService).syncShoppingListsInNewTransaction(eq(testListDtos), eq(testUser), any(LocalDateTime.class));
        verify(itemSyncService).syncShoppingItemsInNewTransaction(eq(testItemDtos), eq(testUser), any(LocalDateTime.class));

        // Verify other sync methods were still called despite the item sync failure
        verify(storeSyncService).syncStoreLocationsInNewTransaction(eq(testStoreDtos), eq(testUser), any(LocalDateTime.class));

        // Verify response contains lists and stores but not items
        assertThat(response).isNotNull();
        assertThat(response.getServerTimestamp()).isNotNull();
        assertThat(response.getShoppingLists()).hasSize(1);
        assertThat(response.getShoppingItems()).isEmpty();
        assertThat(response.getStoreLocations()).hasSize(1);
    }

    @Test
    void synchronize_WhenStoreSyncFails_ShouldContinueWithOtherSyncs() {
        // Arrange
        // Mock deleted items processing
        doNothing().when(deletedItemSyncService).processDeletedItems(anyList(), any(User.class));

        // Mock list sync success
        when(listSyncService.syncShoppingListsInNewTransaction(anyList(), any(User.class), any(LocalDateTime.class)))
                .thenReturn(testListDtos);
        when(listSyncService.getChangedListsFromServer(any(User.class), any(LocalDateTime.class)))
                .thenReturn(new ArrayList<>());

        // Mock item sync success
        when(itemSyncService.syncShoppingItemsInNewTransaction(anyList(), any(User.class), any(LocalDateTime.class)))
                .thenReturn(testItemDtos);
        when(itemSyncService.getChangedItemsFromServer(any(User.class), any(LocalDateTime.class)))
                .thenReturn(new ArrayList<>());

        // Mock store sync to throw exception
        when(storeSyncService.syncStoreLocationsInNewTransaction(anyList(), any(User.class), any(LocalDateTime.class)))
                .thenThrow(new RuntimeException("Test exception"));
        when(storeSyncService.getChangedStoresFromServer(any(User.class), any(LocalDateTime.class)))
                .thenReturn(new ArrayList<>());

        // Act
        SyncResponse response = syncService.synchronize(testRequest, testUser);

        // Assert
        // Verify sync methods were called
        verify(deletedItemSyncService).processDeletedItems(eq(testDeletedItems), eq(testUser));
        verify(listSyncService).syncShoppingListsInNewTransaction(eq(testListDtos), eq(testUser), any(LocalDateTime.class));
        verify(itemSyncService).syncShoppingItemsInNewTransaction(eq(testItemDtos), eq(testUser), any(LocalDateTime.class));
        verify(storeSyncService).syncStoreLocationsInNewTransaction(eq(testStoreDtos), eq(testUser), any(LocalDateTime.class));

        // Verify response contains lists and items but not stores
        assertThat(response).isNotNull();
        assertThat(response.getServerTimestamp()).isNotNull();
        assertThat(response.getShoppingLists()).hasSize(1);
        assertThat(response.getShoppingItems()).hasSize(1);
        assertThat(response.getStoreLocations()).isEmpty();
    }

    @Test
    void synchronize_WhenGetListsFromServerFails_ShouldContinueWithOtherGetMethods() {
        // Arrange
        // Mock client-side sync success
        doNothing().when(deletedItemSyncService).processDeletedItems(anyList(), any(User.class));
        when(listSyncService.syncShoppingListsInNewTransaction(anyList(), any(User.class), any(LocalDateTime.class)))
                .thenReturn(testListDtos);
        when(itemSyncService.syncShoppingItemsInNewTransaction(anyList(), any(User.class), any(LocalDateTime.class)))
                .thenReturn(testItemDtos);
        when(storeSyncService.syncStoreLocationsInNewTransaction(anyList(), any(User.class), any(LocalDateTime.class)))
                .thenReturn(testStoreDtos);

        // Mock getChangedListsFromServer to throw exception
        when(listSyncService.getChangedListsFromServer(any(User.class), any(LocalDateTime.class)))
                .thenThrow(new RuntimeException("Test exception"));

        // Mock other server-side sync methods to succeed
        ShoppingItemDto serverItem = new ShoppingItemDto();
        serverItem.setId(2L);
        serverItem.setName("Server Item");
        serverItem.setSyncId("server-item-sync-id");

        StoreLocationDto serverStore = new StoreLocationDto();
        serverStore.setId(2L);
        serverStore.setName("Server Store");
        serverStore.setSyncId("server-store-sync-id");

        when(itemSyncService.getChangedItemsFromServer(any(User.class), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(serverItem));
        when(storeSyncService.getChangedStoresFromServer(any(User.class), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(serverStore));

        // Act
        SyncResponse response = syncService.synchronize(testRequest, testUser);

        // Assert
        // Verify all sync methods were called
        verify(listSyncService).getChangedListsFromServer(eq(testUser), eq(testLastSync));
        verify(itemSyncService).getChangedItemsFromServer(eq(testUser), eq(testLastSync));
        verify(storeSyncService).getChangedStoresFromServer(eq(testUser), eq(testLastSync));

        // Verify response contains client lists and both client and server items and stores
        assertThat(response).isNotNull();
        assertThat(response.getServerTimestamp()).isNotNull();
        assertThat(response.getShoppingLists()).hasSize(1); // Only client lists
        assertThat(response.getShoppingItems()).hasSize(2); // Client and server items
        assertThat(response.getStoreLocations()).hasSize(2); // Client and server stores

        // Verify server items are included
        assertThat(response.getShoppingItems().stream().map(ShoppingItemDto::getSyncId))
                .contains("test-item-sync-id", "server-item-sync-id");
        assertThat(response.getStoreLocations().stream().map(StoreLocationDto::getSyncId))
                .contains("test-store-sync-id", "server-store-sync-id");
    }

    @Test
    void synchronize_WhenGetItemsFromServerFails_ShouldContinueWithOtherGetMethods() {
        // Arrange
        // Mock client-side sync success
        doNothing().when(deletedItemSyncService).processDeletedItems(anyList(), any(User.class));
        when(listSyncService.syncShoppingListsInNewTransaction(anyList(), any(User.class), any(LocalDateTime.class)))
                .thenReturn(testListDtos);
        when(itemSyncService.syncShoppingItemsInNewTransaction(anyList(), any(User.class), any(LocalDateTime.class)))
                .thenReturn(testItemDtos);
        when(storeSyncService.syncStoreLocationsInNewTransaction(anyList(), any(User.class), any(LocalDateTime.class)))
                .thenReturn(testStoreDtos);

        // Mock server-side sync with getChangedItemsFromServer failing
        ShoppingListDto serverList = new ShoppingListDto();
        serverList.setId(2L);
        serverList.setName("Server Shopping List");
        serverList.setSyncId("server-list-sync-id");

        StoreLocationDto serverStore = new StoreLocationDto();
        serverStore.setId(2L);
        serverStore.setName("Server Store");
        serverStore.setSyncId("server-store-sync-id");

        when(listSyncService.getChangedListsFromServer(any(User.class), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(serverList));
        when(itemSyncService.getChangedItemsFromServer(any(User.class), any(LocalDateTime.class)))
                .thenThrow(new RuntimeException("Test exception"));
        when(storeSyncService.getChangedStoresFromServer(any(User.class), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(serverStore));

        // Act
        SyncResponse response = syncService.synchronize(testRequest, testUser);

        // Assert
        // Verify all sync methods were called
        verify(listSyncService).getChangedListsFromServer(eq(testUser), eq(testLastSync));
        verify(itemSyncService).getChangedItemsFromServer(eq(testUser), eq(testLastSync));
        verify(storeSyncService).getChangedStoresFromServer(eq(testUser), eq(testLastSync));

        // Verify response contains both client and server lists and stores, but only client items
        assertThat(response).isNotNull();
        assertThat(response.getServerTimestamp()).isNotNull();
        assertThat(response.getShoppingLists()).hasSize(2); // Client and server lists
        assertThat(response.getShoppingItems()).hasSize(1); // Only client items
        assertThat(response.getStoreLocations()).hasSize(2); // Client and server stores

        // Verify server lists and stores are included
        assertThat(response.getShoppingLists().stream().map(ShoppingListDto::getSyncId))
                .contains("test-list-sync-id", "server-list-sync-id");
        assertThat(response.getStoreLocations().stream().map(StoreLocationDto::getSyncId))
                .contains("test-store-sync-id", "server-store-sync-id");
    }

    @Test
    void synchronize_WhenGetStoresFromServerFails_ShouldContinueWithOtherGetMethods() {
        // Arrange
        // Mock client-side sync success
        doNothing().when(deletedItemSyncService).processDeletedItems(anyList(), any(User.class));
        when(listSyncService.syncShoppingListsInNewTransaction(anyList(), any(User.class), any(LocalDateTime.class)))
                .thenReturn(testListDtos);
        when(itemSyncService.syncShoppingItemsInNewTransaction(anyList(), any(User.class), any(LocalDateTime.class)))
                .thenReturn(testItemDtos);
        when(storeSyncService.syncStoreLocationsInNewTransaction(anyList(), any(User.class), any(LocalDateTime.class)))
                .thenReturn(testStoreDtos);

        // Mock server-side sync with getChangedStoresFromServer failing
        ShoppingListDto serverList = new ShoppingListDto();
        serverList.setId(2L);
        serverList.setName("Server Shopping List");
        serverList.setSyncId("server-list-sync-id");

        ShoppingItemDto serverItem = new ShoppingItemDto();
        serverItem.setId(2L);
        serverItem.setName("Server Item");
        serverItem.setSyncId("server-item-sync-id");

        when(listSyncService.getChangedListsFromServer(any(User.class), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(serverList));
        when(itemSyncService.getChangedItemsFromServer(any(User.class), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(serverItem));
        when(storeSyncService.getChangedStoresFromServer(any(User.class), any(LocalDateTime.class)))
                .thenThrow(new RuntimeException("Test exception"));

        // Act
        SyncResponse response = syncService.synchronize(testRequest, testUser);

        // Assert
        // Verify all sync methods were called
        verify(listSyncService).getChangedListsFromServer(eq(testUser), eq(testLastSync));
        verify(itemSyncService).getChangedItemsFromServer(eq(testUser), eq(testLastSync));
        verify(storeSyncService).getChangedStoresFromServer(eq(testUser), eq(testLastSync));

        // Verify response contains both client and server lists and items, but only client stores
        assertThat(response).isNotNull();
        assertThat(response.getServerTimestamp()).isNotNull();
        assertThat(response.getShoppingLists()).hasSize(2); // Client and server lists
        assertThat(response.getShoppingItems()).hasSize(2); // Client and server items
        assertThat(response.getStoreLocations()).hasSize(1); // Only client stores

        // Verify server lists and items are included
        assertThat(response.getShoppingLists().stream().map(ShoppingListDto::getSyncId))
                .contains("test-list-sync-id", "server-list-sync-id");
        assertThat(response.getShoppingItems().stream().map(ShoppingItemDto::getSyncId))
                .contains("test-item-sync-id", "server-item-sync-id");
    }

    @Test
    void testMergeMethodsWithNullSyncIds() {
        // Arrange
        // Create lists with some items having null syncIds
        ShoppingListDto clientList = new ShoppingListDto();
        clientList.setId(1L);
        clientList.setName("Client List");
        clientList.setSyncId("client-list-sync-id");

        ShoppingListDto nullSyncIdList = new ShoppingListDto();
        nullSyncIdList.setId(2L);
        nullSyncIdList.setName("Null SyncId List");
        nullSyncIdList.setSyncId(null);

        ShoppingItemDto clientItem = new ShoppingItemDto();
        clientItem.setId(1L);
        clientItem.setName("Client Item");
        clientItem.setSyncId("client-item-sync-id");

        ShoppingItemDto nullSyncIdItem = new ShoppingItemDto();
        nullSyncIdItem.setId(2L);
        nullSyncIdItem.setName("Null SyncId Item");
        nullSyncIdItem.setSyncId(null);

        StoreLocationDto clientStore = new StoreLocationDto();
        clientStore.setId(1L);
        clientStore.setName("Client Store");
        clientStore.setSyncId("client-store-sync-id");

        StoreLocationDto nullSyncIdStore = new StoreLocationDto();
        nullSyncIdStore.setId(2L);
        nullSyncIdStore.setName("Null SyncId Store");
        nullSyncIdStore.setSyncId(null);

        // Create request with lists containing items with null syncIds
        SyncRequest request = new SyncRequest();
        request.setLastSyncTimestamp(testLastSync);
        request.setShoppingLists(Arrays.asList(clientList, nullSyncIdList));
        request.setShoppingItems(Arrays.asList(clientItem, nullSyncIdItem));
        request.setStoreLocations(Arrays.asList(clientStore, nullSyncIdStore));
        request.setDeletedItems(new ArrayList<>());

        // Mock client-side sync to return the same items
        when(listSyncService.syncShoppingListsInNewTransaction(eq(request.getShoppingLists()), eq(testUser), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(clientList, nullSyncIdList));
        when(itemSyncService.syncShoppingItemsInNewTransaction(eq(request.getShoppingItems()), eq(testUser), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(clientItem, nullSyncIdItem));
        when(storeSyncService.syncStoreLocationsInNewTransaction(eq(request.getStoreLocations()), eq(testUser), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(clientStore, nullSyncIdStore));

        // Mock server-side sync to return empty lists
        when(listSyncService.getChangedListsFromServer(eq(testUser), eq(testLastSync)))
                .thenReturn(new ArrayList<>());
        when(itemSyncService.getChangedItemsFromServer(eq(testUser), eq(testLastSync)))
                .thenReturn(new ArrayList<>());
        when(storeSyncService.getChangedStoresFromServer(eq(testUser), eq(testLastSync)))
                .thenReturn(new ArrayList<>());

        // Act
        SyncResponse response = syncService.synchronize(request, testUser);

        // Assert
        // Verify response contains all items, including those with null syncIds
        assertThat(response).isNotNull();
        assertThat(response.getServerTimestamp()).isNotNull();
        assertThat(response.getShoppingLists()).hasSize(2);
        assertThat(response.getShoppingItems()).hasSize(2);
        assertThat(response.getStoreLocations()).hasSize(2);

        // Verify items with null syncIds are included
        List<String> listSyncIds = response.getShoppingLists().stream()
                .map(ShoppingListDto::getSyncId)
                .collect(java.util.stream.Collectors.toList());
        assertThat(listSyncIds).contains("client-list-sync-id");
        assertThat(listSyncIds).containsNull();

        List<String> itemSyncIds = response.getShoppingItems().stream()
                .map(ShoppingItemDto::getSyncId)
                .collect(java.util.stream.Collectors.toList());
        assertThat(itemSyncIds).contains("client-item-sync-id");
        assertThat(itemSyncIds).containsNull();

        List<String> storeSyncIds = response.getStoreLocations().stream()
                .map(StoreLocationDto::getSyncId)
                .collect(java.util.stream.Collectors.toList());
        assertThat(storeSyncIds).contains("client-store-sync-id");
        assertThat(storeSyncIds).containsNull();
    }

    @Test
    void testMergeMethodsWithDuplicateSyncIds() {
        // Arrange
        // Create client lists with duplicate syncIds
        ShoppingListDto clientList = new ShoppingListDto();
        clientList.setId(1L);
        clientList.setName("Client List");
        clientList.setSyncId("duplicate-sync-id");

        ShoppingItemDto clientItem = new ShoppingItemDto();
        clientItem.setId(1L);
        clientItem.setName("Client Item");
        clientItem.setSyncId("duplicate-sync-id");

        StoreLocationDto clientStore = new StoreLocationDto();
        clientStore.setId(1L);
        clientStore.setName("Client Store");
        clientStore.setSyncId("duplicate-sync-id");

        // Create server lists with the same syncIds
        ShoppingListDto serverList = new ShoppingListDto();
        serverList.setId(2L);
        serverList.setName("Server List");
        serverList.setSyncId("duplicate-sync-id");

        ShoppingItemDto serverItem = new ShoppingItemDto();
        serverItem.setId(2L);
        serverItem.setName("Server Item");
        serverItem.setSyncId("duplicate-sync-id");

        StoreLocationDto serverStore = new StoreLocationDto();
        serverStore.setId(2L);
        serverStore.setName("Server Store");
        serverStore.setSyncId("duplicate-sync-id");

        // Create request with client items
        SyncRequest request = new SyncRequest();
        request.setLastSyncTimestamp(testLastSync);
        request.setShoppingLists(Arrays.asList(clientList));
        request.setShoppingItems(Arrays.asList(clientItem));
        request.setStoreLocations(Arrays.asList(clientStore));
        request.setDeletedItems(new ArrayList<>());

        // Mock only the server-side sync methods
        when(listSyncService.getChangedListsFromServer(eq(testUser), eq(testLastSync)))
                .thenReturn(Arrays.asList(serverList));
        when(itemSyncService.getChangedItemsFromServer(eq(testUser), eq(testLastSync)))
                .thenReturn(Arrays.asList(serverItem));
        when(storeSyncService.getChangedStoresFromServer(eq(testUser), eq(testLastSync)))
                .thenReturn(Arrays.asList(serverStore));

        // Mock client-side sync to return the client items
        when(listSyncService.syncShoppingListsInNewTransaction(eq(request.getShoppingLists()), eq(testUser), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(clientList));
        when(itemSyncService.syncShoppingItemsInNewTransaction(eq(request.getShoppingItems()), eq(testUser), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(clientItem));
        when(storeSyncService.syncStoreLocationsInNewTransaction(eq(request.getStoreLocations()), eq(testUser), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(clientStore));

        // Act
        SyncResponse response = syncService.synchronize(request, testUser);

        // Assert
        // Verify response contains only one of each type (no duplicates)
        assertThat(response).isNotNull();
        assertThat(response.getServerTimestamp()).isNotNull();
        assertThat(response.getShoppingLists()).hasSize(1);
        assertThat(response.getShoppingItems()).hasSize(1);
        assertThat(response.getStoreLocations()).hasSize(1);

        // Verify all items have the duplicate syncId
        assertThat(response.getShoppingLists().get(0).getSyncId()).isEqualTo("duplicate-sync-id");
        assertThat(response.getShoppingItems().get(0).getSyncId()).isEqualTo("duplicate-sync-id");
        assertThat(response.getStoreLocations().get(0).getSyncId()).isEqualTo("duplicate-sync-id");
    }
}
