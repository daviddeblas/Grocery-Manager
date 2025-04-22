package com.grocerymanager.api.service.sync;

import com.grocerymanager.api.dto.StoreLocationDto;
import com.grocerymanager.api.model.StoreLocation;
import com.grocerymanager.api.model.User;
import com.grocerymanager.api.repository.StoreLocationRepository;
import com.grocerymanager.api.service.StoreLocationService;
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
public class StoreLocationSyncServiceTest {

    @Mock
    private StoreLocationRepository storeRepository;

    @Mock
    private StoreLocationService storeService;

    @InjectMocks
    private StoreLocationSyncService storeLocationSyncService;

    private User testUser;
    private StoreLocation testStore;
    private StoreLocationDto testStoreDto;
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

        // Set up test store location
        testStore = new StoreLocation();
        testStore.setId(1L);
        testStore.setName("Test Store");
        testStore.setAddress("123 Test Street");
        testStore.setLatitude(40.7128);
        testStore.setLongitude(-74.0060);
        testStore.setGeofenceId("test-geofence-id");
        testStore.setSyncId("test-store-sync-id");
        testStore.setUser(testUser);
        testStore.setCreatedAt(testSyncTime.minusDays(1));
        testStore.setUpdatedAt(testSyncTime.minusDays(1));

        // Set up test store location DTO
        testStoreDto = new StoreLocationDto();
        testStoreDto.setId(1L);
        testStoreDto.setName("Test Store");
        testStoreDto.setAddress("123 Test Street");
        testStoreDto.setLatitude(40.7128);
        testStoreDto.setLongitude(-74.0060);
        testStoreDto.setGeofenceId("test-geofence-id");
        testStoreDto.setSyncId("test-store-sync-id");
        testStoreDto.setCreatedAt(testSyncTime.minusDays(1));
        testStoreDto.setUpdatedAt(testSyncTime.minusDays(1));

        // Set up mock for convertToDto with lenient stubbing
        Mockito.lenient().when(storeService.convertToDto(any(StoreLocation.class))).thenAnswer(invocation -> {
            StoreLocation store = invocation.getArgument(0);
            StoreLocationDto dto = new StoreLocationDto();
            dto.setId(store.getId());
            dto.setName(store.getName());
            dto.setAddress(store.getAddress());
            dto.setLatitude(store.getLatitude());
            dto.setLongitude(store.getLongitude());
            dto.setGeofenceId(store.getGeofenceId());
            dto.setSyncId(store.getSyncId());
            dto.setCreatedAt(store.getCreatedAt());
            dto.setUpdatedAt(store.getUpdatedAt());
            return dto;
        });
    }

    @Test
    void syncStoreLocationsInNewTransaction_ShouldDelegateToSyncStoreLocations() {
        // Arrange
        List<StoreLocationDto> clientStores = Arrays.asList(testStoreDto);
        List<StoreLocationDto> expectedResult = Arrays.asList(testStoreDto);

        // Create a spy on the service to verify the delegation
        StoreLocationSyncService spyService = spy(storeLocationSyncService);
        doReturn(expectedResult).when(spyService).syncStoreLocations(clientStores, testUser, testSyncTime);

        // Act
        List<StoreLocationDto> result = spyService.syncStoreLocationsInNewTransaction(clientStores, testUser, testSyncTime);

        // Assert
        verify(spyService).syncStoreLocations(clientStores, testUser, testSyncTime);
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    void syncStoreLocations_WithEmptyList_ShouldReturnEmptyList() {
        // Arrange
        List<StoreLocationDto> emptyList = new ArrayList<>();

        // Act
        List<StoreLocationDto> result = storeLocationSyncService.syncStoreLocations(emptyList, testUser, testSyncTime);

        // Assert
        assertThat(result).isEmpty();
        verify(storeRepository, never()).findByGeofenceId(anyString());
        verify(storeRepository, never()).findBySyncIdAndUser(anyString(), any(User.class));
        verify(storeRepository, never()).save(any(StoreLocation.class));
    }

    @Test
    void syncStoreLocations_WithNullList_ShouldReturnEmptyList() {
        // Act
        List<StoreLocationDto> result = storeLocationSyncService.syncStoreLocations(null, testUser, testSyncTime);

        // Assert
        assertThat(result).isEmpty();
        verify(storeRepository, never()).findByGeofenceId(anyString());
        verify(storeRepository, never()).findBySyncIdAndUser(anyString(), any(User.class));
        verify(storeRepository, never()).save(any(StoreLocation.class));
    }

    @Test
    void syncStoreLocations_WithExistingStoreByGeofenceId_ShouldUpdateStore() {
        // Arrange
        List<StoreLocationDto> clientStores = Arrays.asList(testStoreDto);
        when(storeRepository.findByGeofenceId("test-geofence-id")).thenReturn(Optional.of(testStore));
        when(storeRepository.save(any(StoreLocation.class))).thenReturn(testStore);

        // Act
        List<StoreLocationDto> result = storeLocationSyncService.syncStoreLocations(clientStores, testUser, testSyncTime);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSyncId()).isEqualTo("test-store-sync-id");
        verify(storeRepository).findByGeofenceId("test-geofence-id");
        verify(storeRepository).save(any(StoreLocation.class));
        verify(storeService).convertToDto(testStore);
    }

    @Test
    void syncStoreLocations_WithExistingStoreBySyncId_ShouldHandleConflict() {
        // Arrange
        List<StoreLocationDto> clientStores = Arrays.asList(testStoreDto);
        when(storeRepository.findByGeofenceId("test-geofence-id")).thenReturn(Optional.empty());
        when(storeRepository.findBySyncIdAndUser("test-store-sync-id", testUser)).thenReturn(Optional.of(testStore));

        // Set client data to be newer
        testStoreDto.setUpdatedAt(testSyncTime);
        testStore.setUpdatedAt(testSyncTime.minusDays(1));

        when(storeRepository.save(any(StoreLocation.class))).thenReturn(testStore);

        // Act
        List<StoreLocationDto> result = storeLocationSyncService.syncStoreLocations(clientStores, testUser, testSyncTime);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSyncId()).isEqualTo("test-store-sync-id");
        verify(storeRepository).findByGeofenceId("test-geofence-id");
        verify(storeRepository).findBySyncIdAndUser("test-store-sync-id", testUser);
        verify(storeRepository).save(any(StoreLocation.class));
        verify(storeService).convertToDto(testStore);
    }

    @Test
    void syncStoreLocations_WithExistingStoreAndServerDataNewer_ShouldReturnServerVersion() {
        // Arrange
        List<StoreLocationDto> clientStores = Arrays.asList(testStoreDto);
        when(storeRepository.findByGeofenceId("test-geofence-id")).thenReturn(Optional.empty());
        when(storeRepository.findBySyncIdAndUser("test-store-sync-id", testUser)).thenReturn(Optional.of(testStore));

        // Set server data to be newer
        testStoreDto.setUpdatedAt(testSyncTime.minusDays(1));
        testStore.setUpdatedAt(testSyncTime);

        // Act
        List<StoreLocationDto> result = storeLocationSyncService.syncStoreLocations(clientStores, testUser, testSyncTime);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSyncId()).isEqualTo("test-store-sync-id");
        verify(storeRepository).findByGeofenceId("test-geofence-id");
        verify(storeRepository).findBySyncIdAndUser("test-store-sync-id", testUser);
        verify(storeRepository, never()).save(any(StoreLocation.class));
        verify(storeService).convertToDto(testStore);
    }

    @Test
    void syncStoreLocations_WithNewStoreWithSyncId_ShouldCreateStore() {
        // Arrange
        StoreLocationDto newStoreDto = new StoreLocationDto();
        newStoreDto.setName("New Store");
        newStoreDto.setAddress("456 New Street");
        newStoreDto.setLatitude(41.8781);
        newStoreDto.setLongitude(-87.6298);
        newStoreDto.setSyncId("new-store-sync-id");

        List<StoreLocationDto> clientStores = Arrays.asList(newStoreDto);
        when(storeRepository.findBySyncIdAndUser("new-store-sync-id", testUser)).thenReturn(Optional.empty());

        StoreLocation savedStore = new StoreLocation();
        savedStore.setId(2L);
        savedStore.setName("New Store");
        savedStore.setAddress("456 New Street");
        savedStore.setLatitude(41.8781);
        savedStore.setLongitude(-87.6298);
        savedStore.setSyncId("new-store-sync-id");
        savedStore.setUser(testUser);
        savedStore.setCreatedAt(testSyncTime);
        savedStore.setUpdatedAt(testSyncTime);

        when(storeRepository.save(any(StoreLocation.class))).thenReturn(savedStore);

        // Act
        List<StoreLocationDto> result = storeLocationSyncService.syncStoreLocations(clientStores, testUser, testSyncTime);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSyncId()).isEqualTo("new-store-sync-id");
        assertThat(result.get(0).getName()).isEqualTo("New Store");
        verify(storeRepository).findBySyncIdAndUser("new-store-sync-id", testUser);
        verify(storeRepository).save(any(StoreLocation.class));
        verify(storeService).convertToDto(savedStore);
    }

    @Test
    void syncStoreLocations_WithNewStoreWithoutSyncId_ShouldCreateStoreWithGeneratedSyncId() {
        // Arrange
        StoreLocationDto newStoreDto = new StoreLocationDto();
        newStoreDto.setName("New Store Without SyncId");
        newStoreDto.setAddress("789 Another Street");
        newStoreDto.setLatitude(34.0522);
        newStoreDto.setLongitude(-118.2437);
        // No syncId

        List<StoreLocationDto> clientStores = Arrays.asList(newStoreDto);

        StoreLocation savedStore = new StoreLocation();
        savedStore.setId(3L);
        savedStore.setName("New Store Without SyncId");
        savedStore.setAddress("789 Another Street");
        savedStore.setLatitude(34.0522);
        savedStore.setLongitude(-118.2437);
        savedStore.setSyncId("generated-sync-id");
        savedStore.setUser(testUser);
        savedStore.setCreatedAt(testSyncTime);
        savedStore.setUpdatedAt(testSyncTime);

        when(storeRepository.save(any(StoreLocation.class))).thenReturn(savedStore);

        // Act
        List<StoreLocationDto> result = storeLocationSyncService.syncStoreLocations(clientStores, testUser, testSyncTime);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("New Store Without SyncId");
        verify(storeRepository).save(any(StoreLocation.class));
        verify(storeService).convertToDto(savedStore);
    }

    @Test
    void syncStoreLocations_WhenRepositoryThrowsException_ShouldContinueWithOtherStores() {
        // Arrange
        StoreLocationDto store1 = new StoreLocationDto();
        store1.setName("Store 1");
        store1.setAddress("Store 1 Address");
        store1.setSyncId("store1-sync-id");

        StoreLocationDto store2 = new StoreLocationDto();
        store2.setName("Store 2");
        store2.setAddress("Store 2 Address");
        store2.setSyncId("store2-sync-id");

        List<StoreLocationDto> clientStores = Arrays.asList(store1, store2);

        // First store processing throws exception
        when(storeRepository.findBySyncIdAndUser("store1-sync-id", testUser)).thenThrow(new RuntimeException("Test exception"));

        // Second store processes successfully
        when(storeRepository.findBySyncIdAndUser("store2-sync-id", testUser)).thenReturn(Optional.empty());

        StoreLocation savedStore2 = new StoreLocation();
        savedStore2.setId(2L);
        savedStore2.setName("Store 2");
        savedStore2.setAddress("Store 2 Address");
        savedStore2.setSyncId("store2-sync-id");
        savedStore2.setUser(testUser);

        when(storeRepository.save(any(StoreLocation.class))).thenReturn(savedStore2);

        // Act
        List<StoreLocationDto> result = storeLocationSyncService.syncStoreLocations(clientStores, testUser, testSyncTime);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSyncId()).isEqualTo("store2-sync-id");
        verify(storeRepository).findBySyncIdAndUser("store1-sync-id", testUser);
        verify(storeRepository).findBySyncIdAndUser("store2-sync-id", testUser);
        verify(storeRepository).save(any(StoreLocation.class));
        verify(storeService).convertToDto(savedStore2);
    }

    @Test
    void getChangedStoresFromServer_WithLastSyncNull_ShouldReturnAllStores() {
        // Arrange
        List<StoreLocation> allStores = Arrays.asList(testStore);
        when(storeRepository.findAllByUser(testUser)).thenReturn(allStores);

        // Act
        List<StoreLocationDto> result = storeLocationSyncService.getChangedStoresFromServer(testUser, null);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSyncId()).isEqualTo("test-store-sync-id");
        verify(storeRepository).findAllByUser(testUser);
        verify(storeRepository, never()).findByUserAndLastSyncedAfter(any(User.class), any(LocalDateTime.class));
        verify(storeService).convertToDto(testStore);
    }

    @Test
    void getChangedStoresFromServer_WithLastSync_ShouldReturnOnlyChangedStores() {
        // Arrange
        LocalDateTime lastSync = testSyncTime.minusHours(1);
        List<StoreLocation> changedStores = Arrays.asList(testStore);
        when(storeRepository.findByUserAndLastSyncedAfter(testUser, lastSync)).thenReturn(changedStores);

        // Act
        List<StoreLocationDto> result = storeLocationSyncService.getChangedStoresFromServer(testUser, lastSync);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSyncId()).isEqualTo("test-store-sync-id");
        verify(storeRepository, never()).findAllByUser(any(User.class));
        verify(storeRepository).findByUserAndLastSyncedAfter(testUser, lastSync);
        verify(storeService).convertToDto(testStore);
    }
}
