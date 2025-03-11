package com.grocerymanager.api.controller;

import com.grocerymanager.api.dto.SyncRequest;
import com.grocerymanager.api.dto.SyncResponse;
import com.grocerymanager.api.model.User;
import com.grocerymanager.api.security.service.UserDetailsImpl;
import com.grocerymanager.api.service.SyncService;
import com.grocerymanager.api.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * Handles data synchronization between the client and the server.
 * - Receives a SyncRequest containing updated data from the client.
 * - Processes the synchronization using SyncService.
 * - Returns a SyncResponse with updated server data.
 * - Ensures authentication before allowing synchronization.
 */
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/sync")
public class SyncController {

    @Autowired
    private SyncService syncService;

    @Autowired
    private UserService userService;

    @PostMapping
    public ResponseEntity<SyncResponse> synchronize(@RequestBody SyncRequest syncRequest) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        SyncResponse response = syncService.synchronize(syncRequest, currentUser);
        return ResponseEntity.ok(response);
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return userService.findById(userDetails.getId()).orElse(null);
    }
}