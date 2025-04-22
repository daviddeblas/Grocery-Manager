package com.grocerymanager.api.controller;

import com.grocerymanager.api.dto.StoreLocationDto;
import com.grocerymanager.api.model.User;
import com.grocerymanager.api.security.service.UserDetailsImpl;
import com.grocerymanager.api.service.StoreLocationService;
import com.grocerymanager.api.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Handles CRUD operations for store locations.
 * - Allows users to retrieve, create, update, and delete stores.
 * - Supports finding nearby stores based on geographic coordinates.
 */
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/stores")
public class StoreLocationController {

    @Autowired
    private StoreLocationService storeLocationService;

    @Autowired
    private UserService userService;

    @GetMapping
    public ResponseEntity<List<StoreLocationDto>> getAllStores() {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<StoreLocationDto> stores = storeLocationService.getAllStoresByUser(currentUser);
        return ResponseEntity.ok(stores);
    }

    @GetMapping("/{id}")
    public ResponseEntity<StoreLocationDto> getStoreById(@PathVariable Long id) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return storeLocationService.getStoreById(id, currentUser)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }



    /**
     * **Finds nearby stores within a given radius.**
     */
    @GetMapping("/nearby")
    public ResponseEntity<List<StoreLocationDto>> getNearbyStores(
            @RequestParam Double latitude,
            @RequestParam Double longitude,
            @RequestParam(defaultValue = "1.0") Double radiusKm) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<StoreLocationDto> nearbyStores = storeLocationService
                .getNearbyStores(currentUser, latitude, longitude, radiusKm);
        return ResponseEntity.ok(nearbyStores);
    }

    @PostMapping
    public ResponseEntity<StoreLocationDto> createStore(@Valid @RequestBody StoreLocationDto storeDto) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        StoreLocationDto createdStore = storeLocationService.createStore(storeDto, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdStore);
    }

    @PutMapping("/{id}")
    public ResponseEntity<StoreLocationDto> updateStore(
            @PathVariable Long id,
            @Valid @RequestBody StoreLocationDto storeDto) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return storeLocationService.updateStore(id, storeDto, currentUser)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStore(@PathVariable Long id) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        boolean deleted = storeLocationService.deleteStore(id, currentUser);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();

        // Handle both UserDetailsImpl and Spring Security's User
        if (principal instanceof UserDetailsImpl) {
            UserDetailsImpl userDetails = (UserDetailsImpl) principal;
            return userService.findById(userDetails.getId()).orElse(null);
        } else if (principal instanceof org.springframework.security.core.userdetails.User) {
            // For tests with @WithMockUser
            String username = ((org.springframework.security.core.userdetails.User) principal).getUsername();
            return userService.findByUsername(username).orElse(null);
        }

        return null;
    }
}
