package com.grocerymanager.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grocerymanager.api.dto.ShoppingListDto;
import com.grocerymanager.api.model.User;
import com.grocerymanager.api.service.ShoppingListService;
import com.grocerymanager.api.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
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
public class ShoppingListControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ShoppingListService shoppingListService;

    @MockBean
    private UserService userService;

    private User testUser;
    private ShoppingListDto testListDto;

    @BeforeEach
    void setUp() {
        // Set up test user
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");

        // Set up user service to return our test user
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        // Set up test list DTO
        testListDto = new ShoppingListDto();
        testListDto.setId(1L);
        testListDto.setName("Test Shopping List");
        testListDto.setSyncId("test-sync-id");
        testListDto.setCreatedAt(LocalDateTime.now());
        testListDto.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    @WithMockUser(username = "testuser")
    void getAllLists_ShouldReturnAllLists() throws Exception {
        // Arrange
        when(shoppingListService.getAllListsByUser(testUser))
                .thenReturn(Arrays.asList(testListDto));

        // Act & Assert
        mockMvc.perform(get("/api/shopping-lists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Test Shopping List"))
                .andExpect(jsonPath("$[0].syncId").value("test-sync-id"));

        verify(userService).findByUsername("testuser");
        verify(shoppingListService).getAllListsByUser(testUser);
    }

    @Test
    @WithMockUser(username = "testuser")
    void getAllLists_WhenNoLists_ShouldReturnEmptyArray() throws Exception {
        // Arrange
        when(shoppingListService.getAllListsByUser(testUser))
                .thenReturn(Collections.emptyList());

        // Act & Assert
        mockMvc.perform(get("/api/shopping-lists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        verify(userService).findByUsername("testuser");
        verify(shoppingListService).getAllListsByUser(testUser);
    }

    @Test
    @WithMockUser(username = "testuser")
    void getListById_WhenListExists_ShouldReturnList() throws Exception {
        // Arrange
        when(shoppingListService.getListById(1L, testUser))
                .thenReturn(Optional.of(testListDto));

        // Act & Assert
        mockMvc.perform(get("/api/shopping-lists/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Test Shopping List"))
                .andExpect(jsonPath("$.syncId").value("test-sync-id"));

        verify(userService).findByUsername("testuser");
        verify(shoppingListService).getListById(1L, testUser);
    }

    @Test
    @WithMockUser(username = "testuser")
    void getListById_WhenListDoesNotExist_ShouldReturn404() throws Exception {
        // Arrange
        when(shoppingListService.getListById(99L, testUser))
                .thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/api/shopping-lists/99"))
                .andExpect(status().isNotFound());

        verify(userService).findByUsername("testuser");
        verify(shoppingListService).getListById(99L, testUser);
    }

    @Test
    @WithMockUser(username = "testuser")
    void createList_ShouldCreateAndReturnList() throws Exception {
        // Arrange
        ShoppingListDto inputDto = new ShoppingListDto();
        inputDto.setName("New Shopping List");

        ShoppingListDto createdDto = new ShoppingListDto();
        createdDto.setId(2L);
        createdDto.setName("New Shopping List");
        createdDto.setSyncId("new-sync-id");
        createdDto.setCreatedAt(LocalDateTime.now());
        createdDto.setUpdatedAt(LocalDateTime.now());

        when(shoppingListService.createList(any(ShoppingListDto.class), eq(testUser)))
                .thenReturn(createdDto);

        // Act & Assert
        mockMvc.perform(post("/api/shopping-lists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.name").value("New Shopping List"))
                .andExpect(jsonPath("$.syncId").value("new-sync-id"));

        verify(userService).findByUsername("testuser");
        verify(shoppingListService).createList(any(ShoppingListDto.class), eq(testUser));
    }

    @Test
    @WithMockUser(username = "testuser")
    void updateList_WhenListExists_ShouldUpdateAndReturnList() throws Exception {
        // Arrange
        ShoppingListDto inputDto = new ShoppingListDto();
        inputDto.setName("Updated Shopping List");

        ShoppingListDto updatedDto = new ShoppingListDto();
        updatedDto.setId(1L);
        updatedDto.setName("Updated Shopping List");
        updatedDto.setSyncId("test-sync-id");
        updatedDto.setCreatedAt(testListDto.getCreatedAt());
        updatedDto.setUpdatedAt(LocalDateTime.now());

        when(shoppingListService.updateList(eq(1L), any(ShoppingListDto.class), eq(testUser)))
                .thenReturn(Optional.of(updatedDto));

        // Act & Assert
        mockMvc.perform(put("/api/shopping-lists/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Updated Shopping List"))
                .andExpect(jsonPath("$.syncId").value("test-sync-id"));

        verify(userService).findByUsername("testuser");
        verify(shoppingListService).updateList(eq(1L), any(ShoppingListDto.class), eq(testUser));
    }

    @Test
    @WithMockUser(username = "testuser")
    void updateList_WhenListDoesNotExist_ShouldReturn404() throws Exception {
        // Arrange
        ShoppingListDto inputDto = new ShoppingListDto();
        inputDto.setName("Updated Shopping List");

        when(shoppingListService.updateList(eq(99L), any(ShoppingListDto.class), eq(testUser)))
                .thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(put("/api/shopping-lists/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputDto)))
                .andExpect(status().isNotFound());

        verify(userService).findByUsername("testuser");
        verify(shoppingListService).updateList(eq(99L), any(ShoppingListDto.class), eq(testUser));
    }

    @Test
    @WithMockUser(username = "testuser")
    void deleteList_WhenListExists_ShouldReturn204() throws Exception {
        // Arrange
        when(shoppingListService.deleteList(1L, testUser))
                .thenReturn(true);

        // Act & Assert
        mockMvc.perform(delete("/api/shopping-lists/1"))
                .andExpect(status().isNoContent());

        verify(userService).findByUsername("testuser");
        verify(shoppingListService).deleteList(1L, testUser);
    }

    @Test
    @WithMockUser(username = "testuser")
    void deleteList_WhenListDoesNotExist_ShouldReturn404() throws Exception {
        // Arrange
        when(shoppingListService.deleteList(99L, testUser))
                .thenReturn(false);

        // Act & Assert
        mockMvc.perform(delete("/api/shopping-lists/99"))
                .andExpect(status().isNotFound());

        verify(userService).findByUsername("testuser");
        verify(shoppingListService).deleteList(99L, testUser);
    }
}