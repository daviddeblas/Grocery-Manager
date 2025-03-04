package com.grocerymanager.api.service;

import com.grocerymanager.api.dto.StoreLocationDto;
import com.grocerymanager.api.model.StoreLocation;
import com.grocerymanager.api.model.User;
import com.grocerymanager.api.repository.StoreLocationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class StoreLocationService {

    @Autowired
    private StoreLocationRepository storeLocationRepository;

    @Transactional(readOnly = true)
    public List<StoreLocationDto> getAllStoresByUser(User user) {
        return storeLocationRepository.findAllByUser(user)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<StoreLocationDto> getStoreById(Long id, User user) {
        return storeLocationRepository.findByIdAndUser(id, user)
                .map(this::convertToDto);
    }

    @Transactional(readOnly = true)
    public List<StoreLocationDto> getNearbyStores(User user, Double latitude, Double longitude, Double radiusKm) {
        Double radiusSquared = Math.pow(radiusKm / 111.0, 2);
        return storeLocationRepository.findNearbyStores(user, latitude, longitude, radiusSquared)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public StoreLocationDto createStore(StoreLocationDto storeDto, User user) {
        StoreLocation store = new StoreLocation();
        store.setName(storeDto.getName());
        store.setAddress(storeDto.getAddress());
        store.setLatitude(storeDto.getLatitude());
        store.setLongitude(storeDto.getLongitude());
        store.setGeofenceId(storeDto.getGeofenceId().isEmpty() ? UUID.randomUUID().toString() : storeDto.getGeofenceId());
        store.setUser(user);
        store.setSyncId(UUID.randomUUID().toString());

        LocalDateTime now = LocalDateTime.now();
        store.setCreatedAt(now);
        store.setUpdatedAt(now);
        store.setLastSynced(now);

        StoreLocation savedStore = storeLocationRepository.save(store);
        return convertToDto(savedStore);
    }

    @Transactional
    public Optional<StoreLocationDto> updateStore(Long id, StoreLocationDto storeDto, User user) {
        return storeLocationRepository.findByIdAndUser(id, user)
                .map(store -> {
                    store.setName(storeDto.getName());
                    store.setAddress(storeDto.getAddress());
                    store.setLatitude(storeDto.getLatitude());
                    store.setLongitude(storeDto.getLongitude());
                    store.setUpdatedAt(LocalDateTime.now());
                    store.setLastSynced(LocalDateTime.now());
                    return convertToDto(storeLocationRepository.save(store));
                });
    }

    @Transactional
    public boolean deleteStore(Long id, User user) {
        return storeLocationRepository.findByIdAndUser(id, user)
                .map(store -> {
                    storeLocationRepository.delete(store);
                    return true;
                })
                .orElse(false);
    }

    public StoreLocationDto convertToDto(StoreLocation store) {
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
        dto.setLastSynced(store.getLastSynced());
        dto.setVersion(store.getVersion());
        return dto;
    }

    public StoreLocation convertToEntity(StoreLocationDto dto, User user) {
        StoreLocation entity = new StoreLocation();
        entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setAddress(dto.getAddress());
        entity.setLatitude(dto.getLatitude());
        entity.setLongitude(dto.getLongitude());
        entity.setGeofenceId(dto.getGeofenceId());
        entity.setUser(user);
        entity.setSyncId(dto.getSyncId());
        entity.setCreatedAt(dto.getCreatedAt());
        entity.setUpdatedAt(dto.getUpdatedAt());
        entity.setLastSynced(dto.getLastSynced());
        return entity;
    }
}