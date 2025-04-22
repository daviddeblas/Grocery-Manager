package com.grocerymanager.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grocerymanager.api.dto.ShoppingItemDto;
import com.grocerymanager.api.model.User;
import com.grocerymanager.api.security.service.UserDetailsImpl;
import com.grocerymanager.api.service.ShoppingItemService;
import com.grocerymanager.api.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class ShoppingItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ShoppingItemService shoppingItemService;

    @MockBean
    private UserService userService;

    private User testUser;
    private ShoppingItemDto testItemDto;

    @BeforeEach
    void setUp() {
        // Set up test user
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");

        // Set up user service to return our test user
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        // Set up test item DTO
        testItemDto = new ShoppingItemDto();
        testItemDto.setId(1L);
        testItemDto.setName("Test Item");
        testItemDto.setShoppingListId(1L);
        testItemDto.setQuantity(2.0);
        testItemDto.setUnitType("pcs");
        testItemDto.setChecked(false);
        testItemDto.setSortIndex(0);
        testItemDto.setCreatedAt(LocalDateTime.now());
        testItemDto.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    @WithMockUser(username = "testuser")
    void getAllItemsByListId_ShouldReturnAllItems() throws Exception {
        // Arrange
        when(shoppingItemService.getAllItemsByListId(1L, testUser))
                .thenReturn(Arrays.asList(testItemDto));

        // Act & Assert
        mockMvc.perform(get("/api/shopping-items/list/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Test Item"))
                .andExpect(jsonPath("$[0].shoppingListId").value(1))
                .andExpect(jsonPath("$[0].quantity").value(2.0))
                .andExpect(jsonPath("$[0].checked").value(false));

        verify(userService).findByUsername("testuser");
        verify(shoppingItemService).getAllItemsByListId(1L, testUser);
    }

    @Test
    @WithMockUser(username = "testuser")
    void getAllItemsByListId_WhenNoItems_ShouldReturnEmptyArray() throws Exception {
        // Arrange
        when(shoppingItemService.getAllItemsByListId(1L, testUser))
                .thenReturn(Collections.emptyList());

        // Act & Assert
        mockMvc.perform(get("/api/shopping-items/list/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        verify(userService).findByUsername("testuser");
        verify(shoppingItemService).getAllItemsByListId(1L, testUser);
    }

    @Test
    @WithMockUser(username = "testuser")
    void getItemById_WhenItemExists_ShouldReturnItem() throws Exception {
        // Arrange
        when(shoppingItemService.getItemById(1L, testUser))
                .thenReturn(Optional.of(testItemDto));

        // Act & Assert
        mockMvc.perform(get("/api/shopping-items/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Test Item"))
                .andExpect(jsonPath("$.shoppingListId").value(1))
                .andExpect(jsonPath("$.quantity").value(2.0))
                .andExpect(jsonPath("$.checked").value(false));

        verify(userService).findByUsername("testuser");
        verify(shoppingItemService).getItemById(1L, testUser);
    }

    @Test
    @WithMockUser(username = "testuser")
    void getItemById_WhenItemDoesNotExist_ShouldReturn404() throws Exception {
        // Arrange
        when(shoppingItemService.getItemById(99L, testUser))
                .thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/api/shopping-items/99"))
                .andExpect(status().isNotFound());

        verify(userService).findByUsername("testuser");
        verify(shoppingItemService).getItemById(99L, testUser);
    }

    @Test
    @WithMockUser(username = "testuser")
    void createItem_ShouldCreateAndReturnItem() throws Exception {
        // Arrange
        ShoppingItemDto inputDto = new ShoppingItemDto();
        inputDto.setName("New Item");
        inputDto.setShoppingListId(1L);
        inputDto.setQuantity(1.0);
        inputDto.setUnitType("pcs");
        inputDto.setChecked(false);
        inputDto.setSortIndex(0);

        ShoppingItemDto createdDto = new ShoppingItemDto();
        createdDto.setId(2L);
        createdDto.setName("New Item");
        createdDto.setShoppingListId(1L);
        createdDto.setQuantity(1.0);
        createdDto.setUnitType("pcs");
        createdDto.setChecked(false);
        createdDto.setSortIndex(0);
        createdDto.setCreatedAt(LocalDateTime.now());
        createdDto.setUpdatedAt(LocalDateTime.now());

        when(shoppingItemService.createItem(any(ShoppingItemDto.class), eq(testUser)))
                .thenReturn(Optional.of(createdDto));

        // Act & Assert
        mockMvc.perform(post("/api/shopping-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.name").value("New Item"))
                .andExpect(jsonPath("$.shoppingListId").value(1))
                .andExpect(jsonPath("$.quantity").value(1.0))
                .andExpect(jsonPath("$.checked").value(false));

        verify(userService).findByUsername("testuser");
        verify(shoppingItemService).createItem(any(ShoppingItemDto.class), eq(testUser));
    }

    @Test
    @WithMockUser(username = "testuser")
    void updateItem_WhenItemExists_ShouldUpdateAndReturnItem() throws Exception {
        // Arrange
        ShoppingItemDto inputDto = new ShoppingItemDto();
        inputDto.setName("Updated Item");
        inputDto.setShoppingListId(1L);
        inputDto.setQuantity(3.0);
        inputDto.setUnitType("pcs");
        inputDto.setChecked(true);
        inputDto.setSortIndex(0);

        ShoppingItemDto updatedDto = new ShoppingItemDto();
        updatedDto.setId(1L);
        updatedDto.setName("Updated Item");
        updatedDto.setShoppingListId(1L);
        updatedDto.setQuantity(3.0);
        updatedDto.setUnitType("pcs");
        updatedDto.setChecked(true);
        updatedDto.setSortIndex(0);
        updatedDto.setCreatedAt(testItemDto.getCreatedAt());
        updatedDto.setUpdatedAt(LocalDateTime.now());

        when(shoppingItemService.updateItem(eq(1L), any(ShoppingItemDto.class), eq(testUser)))
                .thenReturn(Optional.of(updatedDto));

        // Act & Assert
        mockMvc.perform(put("/api/shopping-items/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Updated Item"))
                .andExpect(jsonPath("$.shoppingListId").value(1))
                .andExpect(jsonPath("$.quantity").value(3.0))
                .andExpect(jsonPath("$.checked").value(true));

        verify(userService).findByUsername("testuser");
        verify(shoppingItemService).updateItem(eq(1L), any(ShoppingItemDto.class), eq(testUser));
    }

    @Test
    @WithMockUser(username = "testuser")
    void updateItem_WhenItemDoesNotExist_ShouldReturn404() throws Exception {
        // Arrange
        ShoppingItemDto inputDto = new ShoppingItemDto();
        inputDto.setName("Updated Item");
        inputDto.setShoppingListId(1L);
        inputDto.setQuantity(3.0);
        inputDto.setUnitType("pcs");
        inputDto.setChecked(true);
        inputDto.setSortIndex(0);

        when(shoppingItemService.updateItem(eq(99L), any(ShoppingItemDto.class), eq(testUser)))
                .thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(put("/api/shopping-items/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputDto)))
                .andExpect(status().isNotFound());

        verify(userService).findByUsername("testuser");
        verify(shoppingItemService).updateItem(eq(99L), any(ShoppingItemDto.class), eq(testUser));
    }

    @Test
    @WithMockUser(username = "testuser")
    void deleteItem_WhenItemExists_ShouldReturn204() throws Exception {
        // Arrange
        when(shoppingItemService.deleteItem(1L, testUser))
                .thenReturn(true);

        // Act & Assert
        mockMvc.perform(delete("/api/shopping-items/1"))
                .andExpect(status().isNoContent());

        verify(userService).findByUsername("testuser");
        verify(shoppingItemService).deleteItem(1L, testUser);
    }

    @Test
    @WithMockUser(username = "testuser")
    void deleteItem_WhenItemDoesNotExist_ShouldReturn404() throws Exception {
        // Arrange
        when(shoppingItemService.deleteItem(99L, testUser))
                .thenReturn(false);

        // Act & Assert
        mockMvc.perform(delete("/api/shopping-items/99"))
                .andExpect(status().isNotFound());

        verify(userService).findByUsername("testuser");
        verify(shoppingItemService).deleteItem(99L, testUser);
    }

    @Test
    void getAllItemsByListId_WhenNotAuthenticated_ShouldReturnForbidden() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/shopping-items/list/1"))
                .andExpect(status().isForbidden());

        verify(shoppingItemService, never()).getAllItemsByListId(anyLong(), any());
    }

    @Test
    @WithMockUser(username = "nonexistentuser")
    void getAllItemsByListId_WhenUserNotFound_ShouldReturnUnauthorized() throws Exception {
        // Arrange
        when(userService.findByUsername("nonexistentuser")).thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/api/shopping-items/list/1"))
                .andExpect(status().isUnauthorized());

        verify(userService).findByUsername("nonexistentuser");
        verify(shoppingItemService, never()).getAllItemsByListId(anyLong(), any());
    }

    @Test
    void getAllItemsByListId_WithUserDetailsImpl_ShouldReturnItems() throws Exception {
        // Arrange
        // Mock SecurityContext and Authentication
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        UserDetailsImpl userDetails = mock(UserDetailsImpl.class);

        // Set up the mocks
        when(userDetails.getId()).thenReturn(1L);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(securityContext.getAuthentication()).thenReturn(authentication);

        // Set the security context
        SecurityContextHolder.setContext(securityContext);

        // Set up user service to return our test user
        when(userService.findById(1L)).thenReturn(Optional.of(testUser));

        // Set up shopping item service
        when(shoppingItemService.getAllItemsByListId(1L, testUser))
                .thenReturn(Arrays.asList(testItemDto));

        // Act & Assert
        mockMvc.perform(get("/api/shopping-items/list/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Test Item"));

        verify(userService).findById(1L);
        verify(shoppingItemService).getAllItemsByListId(1L, testUser);

        // Reset the security context
        SecurityContextHolder.clearContext();
    }
}