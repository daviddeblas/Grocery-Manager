package com.grocerymanager.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grocerymanager.api.dto.StoreLocationDto;
import com.grocerymanager.api.model.User;
import com.grocerymanager.api.security.service.UserDetailsImpl;
import com.grocerymanager.api.service.StoreLocationService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class StoreLocationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StoreLocationService storeLocationService;

    @MockBean
    private UserService userService;

    private User testUser;
    private StoreLocationDto testStoreDto;

    @BeforeEach
    void setUp() {
        // Set up test user
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");

        // Set up user service to return our test user
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        // Set up test store DTO
        testStoreDto = new StoreLocationDto();
        testStoreDto.setId(1L);
        testStoreDto.setName("Test Store");
        testStoreDto.setAddress("123 Test Street");
        testStoreDto.setLatitude(40.7128);
        testStoreDto.setLongitude(-74.0060);
        testStoreDto.setGeofenceId(UUID.randomUUID().toString());
        testStoreDto.setSyncId("test-sync-id");
        testStoreDto.setCreatedAt(LocalDateTime.now());
        testStoreDto.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    @WithMockUser(username = "testuser")
    void getAllStores_ShouldReturnAllStores() throws Exception {
        // Arrange
        when(storeLocationService.getAllStoresByUser(testUser))
                .thenReturn(Arrays.asList(testStoreDto));

        // Act & Assert
        mockMvc.perform(get("/api/stores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Test Store"))
                .andExpect(jsonPath("$[0].address").value("123 Test Street"))
                .andExpect(jsonPath("$[0].latitude").value(40.7128))
                .andExpect(jsonPath("$[0].longitude").value(-74.0060))
                .andExpect(jsonPath("$[0].syncId").value("test-sync-id"));

        verify(userService).findByUsername("testuser");
        verify(storeLocationService).getAllStoresByUser(testUser);
    }

    @Test
    @WithMockUser(username = "testuser")
    void getAllStores_WhenNoStores_ShouldReturnEmptyArray() throws Exception {
        // Arrange
        when(storeLocationService.getAllStoresByUser(testUser))
                .thenReturn(Collections.emptyList());

        // Act & Assert
        mockMvc.perform(get("/api/stores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        verify(userService).findByUsername("testuser");
        verify(storeLocationService).getAllStoresByUser(testUser);
    }

    @Test
    @WithMockUser(username = "testuser")
    void getStoreById_WhenStoreExists_ShouldReturnStore() throws Exception {
        // Arrange
        when(storeLocationService.getStoreById(1L, testUser))
                .thenReturn(Optional.of(testStoreDto));

        // Act & Assert
        mockMvc.perform(get("/api/stores/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Test Store"))
                .andExpect(jsonPath("$.address").value("123 Test Street"))
                .andExpect(jsonPath("$.latitude").value(40.7128))
                .andExpect(jsonPath("$.longitude").value(-74.0060))
                .andExpect(jsonPath("$.syncId").value("test-sync-id"));

        verify(userService).findByUsername("testuser");
        verify(storeLocationService).getStoreById(1L, testUser);
    }

    @Test
    @WithMockUser(username = "testuser")
    void getStoreById_WhenStoreDoesNotExist_ShouldReturn404() throws Exception {
        // Arrange
        when(storeLocationService.getStoreById(99L, testUser))
                .thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/api/stores/99"))
                .andExpect(status().isNotFound());

        verify(userService).findByUsername("testuser");
        verify(storeLocationService).getStoreById(99L, testUser);
    }

    @Test
    @WithMockUser(username = "testuser")
    void getNearbyStores_ShouldReturnNearbyStores() throws Exception {
        // Arrange
        when(storeLocationService.getNearbyStores(eq(testUser), eq(40.7128), eq(-74.0060), eq(1.0)))
                .thenReturn(Arrays.asList(testStoreDto));

        // Act & Assert
        mockMvc.perform(get("/api/stores/nearby")
                        .param("latitude", "40.7128")
                        .param("longitude", "-74.0060")
                        .param("radiusKm", "1.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Test Store"))
                .andExpect(jsonPath("$[0].address").value("123 Test Street"))
                .andExpect(jsonPath("$[0].latitude").value(40.7128))
                .andExpect(jsonPath("$[0].longitude").value(-74.0060))
                .andExpect(jsonPath("$[0].syncId").value("test-sync-id"));

        verify(userService).findByUsername("testuser");
        verify(storeLocationService).getNearbyStores(eq(testUser), eq(40.7128), eq(-74.0060), eq(1.0));
    }

    @Test
    @WithMockUser(username = "testuser")
    void createStore_ShouldCreateAndReturnStore() throws Exception {
        // Arrange
        StoreLocationDto inputDto = new StoreLocationDto();
        inputDto.setName("New Store");
        inputDto.setAddress("456 New Street");
        inputDto.setLatitude(41.8781);
        inputDto.setLongitude(-87.6298);
        inputDto.setGeofenceId(UUID.randomUUID().toString());

        StoreLocationDto createdDto = new StoreLocationDto();
        createdDto.setId(2L);
        createdDto.setName("New Store");
        createdDto.setAddress("456 New Street");
        createdDto.setLatitude(41.8781);
        createdDto.setLongitude(-87.6298);
        createdDto.setGeofenceId(UUID.randomUUID().toString());
        createdDto.setSyncId("new-sync-id");
        createdDto.setCreatedAt(LocalDateTime.now());
        createdDto.setUpdatedAt(LocalDateTime.now());

        when(storeLocationService.createStore(any(StoreLocationDto.class), eq(testUser)))
                .thenReturn(createdDto);

        // Act & Assert
        mockMvc.perform(post("/api/stores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.name").value("New Store"))
                .andExpect(jsonPath("$.address").value("456 New Street"))
                .andExpect(jsonPath("$.latitude").value(41.8781))
                .andExpect(jsonPath("$.longitude").value(-87.6298))
                .andExpect(jsonPath("$.syncId").value("new-sync-id"));

        verify(userService).findByUsername("testuser");
        verify(storeLocationService).createStore(any(StoreLocationDto.class), eq(testUser));
    }

    @Test
    @WithMockUser(username = "testuser")
    void updateStore_WhenStoreExists_ShouldUpdateAndReturnStore() throws Exception {
        // Arrange
        StoreLocationDto inputDto = new StoreLocationDto();
        inputDto.setName("Updated Store");
        inputDto.setAddress("789 Updated Street");
        inputDto.setLatitude(34.0522);
        inputDto.setLongitude(-118.2437);
        inputDto.setGeofenceId(UUID.randomUUID().toString());

        StoreLocationDto updatedDto = new StoreLocationDto();
        updatedDto.setId(1L);
        updatedDto.setName("Updated Store");
        updatedDto.setAddress("789 Updated Street");
        updatedDto.setLatitude(34.0522);
        updatedDto.setLongitude(-118.2437);
        updatedDto.setGeofenceId(testStoreDto.getGeofenceId());
        updatedDto.setSyncId("test-sync-id");
        updatedDto.setCreatedAt(testStoreDto.getCreatedAt());
        updatedDto.setUpdatedAt(LocalDateTime.now());

        when(storeLocationService.updateStore(eq(1L), any(StoreLocationDto.class), eq(testUser)))
                .thenReturn(Optional.of(updatedDto));

        // Act & Assert
        mockMvc.perform(put("/api/stores/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Updated Store"))
                .andExpect(jsonPath("$.address").value("789 Updated Street"))
                .andExpect(jsonPath("$.latitude").value(34.0522))
                .andExpect(jsonPath("$.longitude").value(-118.2437))
                .andExpect(jsonPath("$.syncId").value("test-sync-id"));

        verify(userService).findByUsername("testuser");
        verify(storeLocationService).updateStore(eq(1L), any(StoreLocationDto.class), eq(testUser));
    }

    @Test
    @WithMockUser(username = "testuser")
    void updateStore_WhenStoreDoesNotExist_ShouldReturn404() throws Exception {
        // Arrange
        StoreLocationDto inputDto = new StoreLocationDto();
        inputDto.setName("Updated Store");
        inputDto.setAddress("789 Updated Street");
        inputDto.setLatitude(34.0522);
        inputDto.setLongitude(-118.2437);
        inputDto.setGeofenceId(UUID.randomUUID().toString());

        when(storeLocationService.updateStore(eq(99L), any(StoreLocationDto.class), eq(testUser)))
                .thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(put("/api/stores/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputDto)))
                .andExpect(status().isNotFound());

        verify(userService).findByUsername("testuser");
        verify(storeLocationService).updateStore(eq(99L), any(StoreLocationDto.class), eq(testUser));
    }

    @Test
    @WithMockUser(username = "testuser")
    void deleteStore_WhenStoreExists_ShouldReturn204() throws Exception {
        // Arrange
        when(storeLocationService.deleteStore(1L, testUser))
                .thenReturn(true);

        // Act & Assert
        mockMvc.perform(delete("/api/stores/1"))
                .andExpect(status().isNoContent());

        verify(userService).findByUsername("testuser");
        verify(storeLocationService).deleteStore(1L, testUser);
    }

    @Test
    @WithMockUser(username = "testuser")
    void deleteStore_WhenStoreDoesNotExist_ShouldReturn404() throws Exception {
        // Arrange
        when(storeLocationService.deleteStore(99L, testUser))
                .thenReturn(false);

        // Act & Assert
        mockMvc.perform(delete("/api/stores/99"))
                .andExpect(status().isNotFound());

        verify(userService).findByUsername("testuser");
        verify(storeLocationService).deleteStore(99L, testUser);
    }

    @Test
    void getAllStores_WhenNotAuthenticated_ShouldReturnForbidden() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/stores"))
                .andExpect(status().isForbidden());

        verify(storeLocationService, never()).getAllStoresByUser(any());
    }

    @Test
    @WithMockUser(username = "nonexistentuser")
    void getAllStores_WhenUserNotFound_ShouldReturnUnauthorized() throws Exception {
        // Arrange
        when(userService.findByUsername("nonexistentuser")).thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/api/stores"))
                .andExpect(status().isUnauthorized());

        verify(userService).findByUsername("nonexistentuser");
        verify(storeLocationService, never()).getAllStoresByUser(any());
    }

    @Test
    void getAllStores_WithUserDetailsImpl_ShouldReturnStores() throws Exception {
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

        // Set up store location service
        when(storeLocationService.getAllStoresByUser(testUser))
                .thenReturn(Arrays.asList(testStoreDto));

        // Act & Assert
        mockMvc.perform(get("/api/stores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Test Store"));

        verify(userService).findById(1L);
        verify(storeLocationService).getAllStoresByUser(testUser);

        // Reset the security context
        SecurityContextHolder.clearContext();
    }
}