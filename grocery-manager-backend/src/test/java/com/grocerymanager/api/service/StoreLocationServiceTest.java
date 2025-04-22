package com.grocerymanager.api.service;

import com.grocerymanager.api.dto.StoreLocationDto;
import com.grocerymanager.api.model.StoreLocation;
import com.grocerymanager.api.model.User;
import com.grocerymanager.api.repository.StoreLocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StoreLocationServiceTest {

    @Mock
    private StoreLocationRepository storeLocationRepository;

    @InjectMocks
    private StoreLocationService storeLocationService;

    private User testUser;
    private StoreLocation testStore;
    private StoreLocationDto testStoreDto;

    @BeforeEach
    void setUp() {
        // Create test user
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");

        // Create a test store location
        testStore = new StoreLocation();
        testStore.setId(1L);
        testStore.setName("Test Store");
        testStore.setAddress("123 Test Street");
        testStore.setLatitude(40.7128);
        testStore.setLongitude(-74.0060);
        testStore.setGeofenceId(UUID.randomUUID().toString());
        testStore.setUser(testUser);
        testStore.setSyncId(UUID.randomUUID().toString());
        testStore.setCreatedAt(LocalDateTime.now());
        testStore.setUpdatedAt(LocalDateTime.now());

        // Create a test DTO
        testStoreDto = new StoreLocationDto();
        testStoreDto.setId(1L);
        testStoreDto.setName("Test Store");
        testStoreDto.setAddress("123 Test Street");
        testStoreDto.setLatitude(40.7128);
        testStoreDto.setLongitude(-74.0060);
        testStoreDto.setGeofenceId(testStore.getGeofenceId());
        testStoreDto.setSyncId(testStore.getSyncId());
        testStoreDto.setCreatedAt(testStore.getCreatedAt());
        testStoreDto.setUpdatedAt(testStore.getUpdatedAt());
    }

    @Test
    void getAllStoresByUser_ShouldReturnAllStores() {
        // Arrange
        when(storeLocationRepository.findAllByUser(testUser))
                .thenReturn(Arrays.asList(testStore));

        // Act
        List<StoreLocationDto> result = storeLocationService.getAllStoresByUser(testUser);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo(testStore.getName());
        assertThat(result.get(0).getAddress()).isEqualTo(testStore.getAddress());
        assertThat(result.get(0).getLatitude()).isEqualTo(testStore.getLatitude());
        assertThat(result.get(0).getLongitude()).isEqualTo(testStore.getLongitude());
        assertThat(result.get(0).getGeofenceId()).isEqualTo(testStore.getGeofenceId());
        verify(storeLocationRepository, times(1)).findAllByUser(testUser);
    }

    @Test
    void getStoreById_WhenStoreExists_ShouldReturnStore() {
        // Arrange
        when(storeLocationRepository.findByIdAndUser(1L, testUser))
                .thenReturn(Optional.of(testStore));

        // Act
        Optional<StoreLocationDto> result = storeLocationService.getStoreById(1L, testUser);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo(testStore.getName());
        assertThat(result.get().getAddress()).isEqualTo(testStore.getAddress());
        assertThat(result.get().getSyncId()).isEqualTo(testStore.getSyncId());
        verify(storeLocationRepository, times(1)).findByIdAndUser(1L, testUser);
    }

    @Test
    void getStoreById_WhenStoreDoesNotExist_ShouldReturnEmpty() {
        // Arrange
        when(storeLocationRepository.findByIdAndUser(99L, testUser))
                .thenReturn(Optional.empty());

        // Act
        Optional<StoreLocationDto> result = storeLocationService.getStoreById(99L, testUser);

        // Assert
        assertThat(result).isEmpty();
        verify(storeLocationRepository, times(1)).findByIdAndUser(99L, testUser);
    }

    @Test
    void getNearbyStores_ShouldReturnStoresWithinRadius() {
        // Arrange
        double latitude = 40.7128;
        double longitude = -74.0060;
        double radiusKm = 2.0;
        double radiusSquared = Math.pow(radiusKm / 111.0, 2);

        when(storeLocationRepository.findNearbyStores(testUser, latitude, longitude, radiusSquared))
                .thenReturn(Arrays.asList(testStore));

        // Act
        List<StoreLocationDto> result = storeLocationService.getNearbyStores(testUser, latitude, longitude, radiusKm);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo(testStore.getName());
        verify(storeLocationRepository, times(1)).findNearbyStores(eq(testUser), eq(latitude), eq(longitude), anyDouble());
    }

    @Test
    void createStore_ShouldCreateAndReturnNewStore() {
        // Arrange
        StoreLocationDto inputDto = new StoreLocationDto();
        inputDto.setName("New Store");
        inputDto.setAddress("456 New Street");
        inputDto.setLatitude(41.8781);
        inputDto.setLongitude(-87.6298);
        inputDto.setGeofenceId(UUID.randomUUID().toString());

        when(storeLocationRepository.save(any(StoreLocation.class)))
                .thenAnswer(invocation -> {
                    StoreLocation savedStore = invocation.getArgument(0);
                    savedStore.setId(2L);  // Simulate database assigning ID
                    return savedStore;
                });

        // Act
        StoreLocationDto result = storeLocationService.createStore(inputDto, testUser);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("New Store");
        assertThat(result.getAddress()).isEqualTo("456 New Street");
        assertThat(result.getSyncId()).isNotNull();  // Should have generated a syncId
        verify(storeLocationRepository, times(1)).save(any(StoreLocation.class));
    }

    @Test
    void updateStore_WhenStoreExists_ShouldUpdateAndReturnStore() {
        // Arrange
        StoreLocationDto updateDto = new StoreLocationDto();
        updateDto.setName("Updated Store");
        updateDto.setAddress("789 Updated Street");
        updateDto.setLatitude(42.3601);
        updateDto.setLongitude(-71.0589);

        when(storeLocationRepository.findByIdAndUser(1L, testUser))
                .thenReturn(Optional.of(testStore));
        when(storeLocationRepository.save(any(StoreLocation.class)))
                .thenReturn(testStore);

        // Act
        Optional<StoreLocationDto> result = storeLocationService.updateStore(1L, updateDto, testUser);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Updated Store");
        assertThat(result.get().getAddress()).isEqualTo("789 Updated Street");
        assertThat(result.get().getLatitude()).isEqualTo(42.3601);
        assertThat(result.get().getLongitude()).isEqualTo(-71.0589);
        verify(storeLocationRepository, times(1)).findByIdAndUser(1L, testUser);
        verify(storeLocationRepository, times(1)).save(any(StoreLocation.class));
    }

    @Test
    void updateStore_WhenStoreDoesNotExist_ShouldReturnEmpty() {
        // Arrange
        StoreLocationDto updateDto = new StoreLocationDto();
        updateDto.setName("Updated Store");

        when(storeLocationRepository.findByIdAndUser(99L, testUser))
                .thenReturn(Optional.empty());

        // Act
        Optional<StoreLocationDto> result = storeLocationService.updateStore(99L, updateDto, testUser);

        // Assert
        assertThat(result).isEmpty();
        verify(storeLocationRepository, times(1)).findByIdAndUser(99L, testUser);
        verify(storeLocationRepository, never()).save(any(StoreLocation.class));
    }

    @Test
    void deleteStore_WhenStoreExists_ShouldDeleteAndReturnTrue() {
        // Arrange
        when(storeLocationRepository.findByIdAndUser(1L, testUser))
                .thenReturn(Optional.of(testStore));
        doNothing().when(storeLocationRepository).delete(testStore);

        // Act
        boolean result = storeLocationService.deleteStore(1L, testUser);

        // Assert
        assertThat(result).isTrue();
        verify(storeLocationRepository, times(1)).findByIdAndUser(1L, testUser);
        verify(storeLocationRepository, times(1)).delete(testStore);
    }

    @Test
    void deleteStore_WhenStoreDoesNotExist_ShouldReturnFalse() {
        // Arrange
        when(storeLocationRepository.findByIdAndUser(99L, testUser))
                .thenReturn(Optional.empty());

        // Act
        boolean result = storeLocationService.deleteStore(99L, testUser);

        // Assert
        assertThat(result).isFalse();
        verify(storeLocationRepository, times(1)).findByIdAndUser(99L, testUser);
        verify(storeLocationRepository, never()).delete(any());
    }

    @Test
    void findByGeofenceIdAndUser_WhenStoreExists_ShouldReturnStore() {
        // Arrange
        String geofenceId = testStore.getGeofenceId();

        when(storeLocationRepository.findByGeofenceId(geofenceId))
                .thenReturn(Optional.of(testStore));

        // Act
        Optional<StoreLocationDto> result = storeLocationService.findByGeofenceIdAndUser(geofenceId, testUser);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo(testStore.getName());
        assertThat(result.get().getGeofenceId()).isEqualTo(geofenceId);
        verify(storeLocationRepository, times(1)).findByGeofenceId(geofenceId);
    }

    @Test
    void findByGeofenceIdAndUser_WhenStoreDoesNotExist_ShouldReturnEmpty() {
        // Arrange
        String nonExistentGeofenceId = "non-existent-id";

        when(storeLocationRepository.findByGeofenceId(nonExistentGeofenceId))
                .thenReturn(Optional.empty());

        // Act
        Optional<StoreLocationDto> result = storeLocationService.findByGeofenceIdAndUser(nonExistentGeofenceId, testUser);

        // Assert
        assertThat(result).isEmpty();
        verify(storeLocationRepository, times(1)).findByGeofenceId(nonExistentGeofenceId);
    }

    @Test
    void findByGeofenceIdAndUser_WhenStoreBelongsToDifferentUser_ShouldReturnEmpty() {
        // Arrange
        String geofenceId = testStore.getGeofenceId();
        User differentUser = new User();
        differentUser.setId(2L);

        when(storeLocationRepository.findByGeofenceId(geofenceId))
                .thenReturn(Optional.of(testStore));

        // Act
        Optional<StoreLocationDto> result = storeLocationService.findByGeofenceIdAndUser(geofenceId, differentUser);

        // Assert
        assertThat(result).isEmpty();
        verify(storeLocationRepository, times(1)).findByGeofenceId(geofenceId);
    }

    @Test
    void convertToDto_ShouldCorrectlyConvertEntityToDto() {
        // Act
        StoreLocationDto result = storeLocationService.convertToDto(testStore);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(testStore.getId());
        assertThat(result.getName()).isEqualTo(testStore.getName());
        assertThat(result.getAddress()).isEqualTo(testStore.getAddress());
        assertThat(result.getLatitude()).isEqualTo(testStore.getLatitude());
        assertThat(result.getLongitude()).isEqualTo(testStore.getLongitude());
        assertThat(result.getGeofenceId()).isEqualTo(testStore.getGeofenceId());
        assertThat(result.getSyncId()).isEqualTo(testStore.getSyncId());
        assertThat(result.getCreatedAt()).isEqualTo(testStore.getCreatedAt());
        assertThat(result.getUpdatedAt()).isEqualTo(testStore.getUpdatedAt());
    }

    @Test
    void convertToEntity_ShouldCorrectlyConvertDtoToEntity() {
        // Act
        StoreLocation result = storeLocationService.convertToEntity(testStoreDto, testUser);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(testStoreDto.getId());
        assertThat(result.getName()).isEqualTo(testStoreDto.getName());
        assertThat(result.getAddress()).isEqualTo(testStoreDto.getAddress());
        assertThat(result.getLatitude()).isEqualTo(testStoreDto.getLatitude());
        assertThat(result.getLongitude()).isEqualTo(testStoreDto.getLongitude());
        assertThat(result.getGeofenceId()).isEqualTo(testStoreDto.getGeofenceId());
        assertThat(result.getSyncId()).isEqualTo(testStoreDto.getSyncId());
        assertThat(result.getCreatedAt()).isEqualTo(testStoreDto.getCreatedAt());
        assertThat(result.getUpdatedAt()).isEqualTo(testStoreDto.getUpdatedAt());
        assertThat(result.getUser()).isEqualTo(testUser);
    }
}