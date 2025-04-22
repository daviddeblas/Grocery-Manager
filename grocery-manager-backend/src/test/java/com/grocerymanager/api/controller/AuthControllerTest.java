package com.grocerymanager.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grocerymanager.api.dto.auth.LoginRequest;
import com.grocerymanager.api.dto.auth.SignupRequest;
import com.grocerymanager.api.security.jwt.JwtUtils;
import com.grocerymanager.api.security.service.UserDetailsImpl;
import com.grocerymanager.api.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthenticationManager authenticationManager;

    @MockBean
    private JwtUtils jwtUtils;

    @MockBean
    private UserService userService;

    private LoginRequest loginRequest;
    private SignupRequest signupRequest;
    private Authentication authentication;
    private UserDetailsImpl userDetails;

    @BeforeEach
    void setUp() {
        // Login request
        loginRequest = new LoginRequest();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword("password");

        // Signup request
        signupRequest = new SignupRequest();
        signupRequest.setUsername("newuser");
        signupRequest.setEmail("newuser@example.com");
        signupRequest.setPassword("password123");

        // Mock authentication
        userDetails = mock(UserDetailsImpl.class);
        when(userDetails.getId()).thenReturn(1L);
        when(userDetails.getUsername()).thenReturn("testuser");
        when(userDetails.getEmail()).thenReturn("test@example.com");

        authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(userDetails);
    }

    @Test
    void authenticateUser_ShouldReturnJwtResponse() throws Exception {
        // Arrange
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(jwtUtils.generateJwtToken(authentication)).thenReturn("mocked.jwt.token");

        // Act & Assert
        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mocked.jwt.token"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.email").value("test@example.com"));

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(jwtUtils).generateJwtToken(authentication);
    }

    @Test
    void registerUser_WhenUsernameIsTaken_ShouldReturnBadRequest() throws Exception {
        // Arrange
        when(userService.existsByUsername("newuser")).thenReturn(true);

        // Act & Assert
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This username is already taken!"));

        verify(userService).existsByUsername("newuser");
        verify(userService, never()).existsByEmail(any());
        verify(userService, never()).createUser(any());
    }

    @Test
    void registerUser_WhenEmailIsTaken_ShouldReturnBadRequest() throws Exception {
        // Arrange
        when(userService.existsByUsername("newuser")).thenReturn(false);
        when(userService.existsByEmail("newuser@example.com")).thenReturn(true);

        // Act & Assert
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This email is already in use!"));

        verify(userService).existsByUsername("newuser");
        verify(userService).existsByEmail("newuser@example.com");
        verify(userService, never()).createUser(any());
    }

    @Test
    void registerUser_WhenValidRequest_ShouldRegisterUserAndReturnSuccess() throws Exception {
        // Arrange
        when(userService.existsByUsername("newuser")).thenReturn(false);
        when(userService.existsByEmail("newuser@example.com")).thenReturn(false);
        doNothing().when(userService).createUser(any(SignupRequest.class));

        // Act & Assert
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User registered successfully!"));

        verify(userService).existsByUsername("newuser");
        verify(userService).existsByEmail("newuser@example.com");
        verify(userService).createUser(any(SignupRequest.class));
    }

    @Test
    @WithMockUser
    void logoutUser_ShouldReturnSuccessMessage() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/auth/signout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Log out successful!"));
    }

    @Test
    void sendCredentials_WhenEmailExists_ShouldReturnSuccess() throws Exception {
        // Arrange
        AuthController.ForgotPasswordRequest request = new AuthController.ForgotPasswordRequest();
        request.setEmail("test@example.com");

        when(userService.sendCredentials("test@example.com")).thenReturn(true);

        // Act & Assert
        mockMvc.perform(post("/api/auth/send-credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("If an account exists with that email, we've sent the login credentials."));

        verify(userService).sendCredentials("test@example.com");
    }

    @Test
    void sendCredentials_WhenEmailDoesNotExist_ShouldStillReturnSuccess() throws Exception {
        // Arrange
        AuthController.ForgotPasswordRequest request = new AuthController.ForgotPasswordRequest();
        request.setEmail("nonexistent@example.com");

        when(userService.sendCredentials("nonexistent@example.com")).thenReturn(false);

        // Act & Assert
        mockMvc.perform(post("/api/auth/send-credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("If an account exists with that email, we've sent the login credentials."));

        verify(userService).sendCredentials("nonexistent@example.com");
    }

    @Test
    void authenticateUser_WhenInvalidCredentials_ShouldReturnForbidden() throws Exception {
        // Arrange
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new org.springframework.security.authentication.BadCredentialsException("Bad credentials"));

        // Act & Assert
        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isForbidden());

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(jwtUtils, never()).generateJwtToken(any());
    }
}