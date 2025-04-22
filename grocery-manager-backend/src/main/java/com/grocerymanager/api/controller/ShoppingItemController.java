package com.grocerymanager.api.controller;

import com.grocerymanager.api.dto.ShoppingItemDto;
import com.grocerymanager.api.model.User;
import com.grocerymanager.api.security.service.UserDetailsImpl;
import com.grocerymanager.api.service.ShoppingItemService;
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
 * Handles operations related to shopping items:
 * - Fetching all items in a shopping list (`/list/{listId}`)
 * - Fetching a specific item (`/{id}`)
 * - Creating a new item (`POST /`)
 * - Updating an item (`PUT /{id}`)
 * - Deleting an item (`DELETE /{id}`)
 */
// For each request, the client sends a OPTIONS request to the server to check if the server allows the request.
// So by setting the maxAge to 3600 seconds, the client will only send the OPTIONS request once every hour.
// This is to allow the client to cache the response from the server.
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/shopping-items")
public class ShoppingItemController {

    @Autowired
    private ShoppingItemService shoppingItemService;

    @Autowired
    private UserService userService;

    @GetMapping("/list/{listId}")
    public ResponseEntity<List<ShoppingItemDto>> getAllItemsByListId(@PathVariable Long listId) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<ShoppingItemDto> items = shoppingItemService.getAllItemsByListId(listId, currentUser);
        return ResponseEntity.ok(items);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShoppingItemDto> getItemById(@PathVariable Long id) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return shoppingItemService.getItemById(id, currentUser)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ShoppingItemDto> createItem(@Valid @RequestBody ShoppingItemDto itemDto) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return shoppingItemService.createItem(itemDto, currentUser)
                .map(item -> ResponseEntity.status(HttpStatus.CREATED).body(item))
                .orElse(ResponseEntity.badRequest().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ShoppingItemDto> updateItem(
            @PathVariable Long id,
            @Valid @RequestBody ShoppingItemDto itemDto) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return shoppingItemService.updateItem(id, itemDto, currentUser)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteItem(@PathVariable Long id) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        boolean deleted = shoppingItemService.deleteItem(id, currentUser);
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
