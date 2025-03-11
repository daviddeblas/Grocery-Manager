package com.grocerymanager.api.controller;

import com.grocerymanager.api.dto.ShoppingListDto;
import com.grocerymanager.api.model.User;
import com.grocerymanager.api.security.service.UserDetailsImpl;
import com.grocerymanager.api.service.ShoppingListService;
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
 * Handles **CRUD operations** for shopping lists.
 * - Retrieves, creates, updates, and deletes shopping lists.
 * - Ensures users can only access their own shopping lists.
 */
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/shopping-lists")
public class ShoppingListController {

    @Autowired
    private ShoppingListService shoppingListService;

    @Autowired
    private UserService userService;

    @GetMapping
    public ResponseEntity<List<ShoppingListDto>> getAllLists() {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<ShoppingListDto> lists = shoppingListService.getAllListsByUser(currentUser);
        return ResponseEntity.ok(lists);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShoppingListDto> getListById(@PathVariable Long id) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return shoppingListService.getListById(id, currentUser)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ShoppingListDto> createList(@Valid @RequestBody ShoppingListDto listDto) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        ShoppingListDto createdList = shoppingListService.createList(listDto, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdList);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ShoppingListDto> updateList(@PathVariable Long id, @Valid @RequestBody ShoppingListDto listDto) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return shoppingListService.updateList(id, listDto, currentUser)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteList(@PathVariable Long id) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        boolean deleted = shoppingListService.deleteList(id, currentUser);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
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