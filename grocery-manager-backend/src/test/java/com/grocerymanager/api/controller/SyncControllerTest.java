package com.grocerymanager.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grocerymanager.api.dto.SyncRequest;
import com.grocerymanager.api.dto.SyncResponse;
import com.grocerymanager.api.model.User;
import com.grocerymanager.api.security.service.UserDetailsImpl;
import com.grocerymanager.api.service.UserService;
import com.grocerymanager.api.service.sync.SyncService;
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
import java.util.ArrayList;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class SyncControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SyncService syncService;

    @MockBean
    private UserService userService;

    private User testUser;
    private SyncRequest testSyncRequest;
    private SyncResponse testSyncResponse;

    @BeforeEach
    void setUp() {
        // Set up test user
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");

        // Set up user service to return our test user
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        // Set up test sync request
        testSyncRequest = new SyncRequest();
        testSyncRequest.setShoppingLists(new ArrayList<>());
        testSyncRequest.setShoppingItems(new ArrayList<>());
        testSyncRequest.setStoreLocations(new ArrayList<>());
        testSyncRequest.setDeletedItems(new ArrayList<>());

        // Set up test sync response
        testSyncResponse = new SyncResponse();
        testSyncResponse.setServerTimestamp(LocalDateTime.now());
        testSyncResponse.setShoppingLists(new ArrayList<>());
        testSyncResponse.setShoppingItems(new ArrayList<>());
        testSyncResponse.setStoreLocations(new ArrayList<>());
    }

    @Test
    @WithMockUser(username = "testuser")
    void synchronize_ShouldReturnSyncResponse() throws Exception {
        // Arrange
        when(syncService.synchronize(any(SyncRequest.class), eq(testUser)))
                .thenReturn(testSyncResponse);

        // Act & Assert
        mockMvc.perform(post("/api/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testSyncRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serverTimestamp").exists())
                .andExpect(jsonPath("$.shoppingLists").isArray())
                .andExpect(jsonPath("$.shoppingItems").isArray())
                .andExpect(jsonPath("$.storeLocations").isArray());

        verify(userService).findByUsername("testuser");
        verify(syncService).synchronize(any(SyncRequest.class), eq(testUser));
    }

    @Test
    void synchronize_WhenNotAuthenticated_ShouldReturnForbidden() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testSyncRequest)))
                .andExpect(status().isForbidden());

        verify(syncService, never()).synchronize(any(), any());
    }

    @Test
    @WithMockUser(username = "nonexistentuser")
    void synchronize_WhenUserNotFound_ShouldReturnUnauthorized() throws Exception {
        // Arrange
        when(userService.findByUsername("nonexistentuser")).thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(post("/api/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testSyncRequest)))
                .andExpect(status().isUnauthorized());

        verify(userService).findByUsername("nonexistentuser");
        verify(syncService, never()).synchronize(any(), any());
    }

    @Test
    void synchronize_WithUserDetailsImpl_ShouldReturnSyncResponse() throws Exception {
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

        // Set up sync service
        when(syncService.synchronize(any(SyncRequest.class), eq(testUser)))
                .thenReturn(testSyncResponse);

        // Act & Assert
        mockMvc.perform(post("/api/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testSyncRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serverTimestamp").exists())
                .andExpect(jsonPath("$.shoppingLists").isArray())
                .andExpect(jsonPath("$.shoppingItems").isArray())
                .andExpect(jsonPath("$.storeLocations").isArray());

        verify(userService).findById(1L);
        verify(syncService).synchronize(any(SyncRequest.class), eq(testUser));

        // Reset the security context
        SecurityContextHolder.clearContext();
    }
}
