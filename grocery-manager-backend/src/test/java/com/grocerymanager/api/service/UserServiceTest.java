package com.grocerymanager.api.service;

import com.grocerymanager.api.dto.auth.SignupRequest;
import com.grocerymanager.api.model.User;
import com.grocerymanager.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private SignupRequest signupRequest;

    @BeforeEach
    void setUp() {
        // Create test user
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setPassword("encodedPassword");

        // Create signup request
        signupRequest = new SignupRequest();
        signupRequest.setUsername("newuser");
        signupRequest.setEmail("newuser@example.com");
        signupRequest.setPassword("password123");
    }

    @Test
    void createUser_ShouldCreateUserWithEncodedPassword() {
        // Arrange
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword123");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        userService.createUser(signupRequest);

        // Assert
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getUsername()).isEqualTo("newuser");
        assertThat(savedUser.getEmail()).isEqualTo("newuser@example.com");
        assertThat(savedUser.getPassword()).isEqualTo("encodedPassword123");
    }

    @Test
    void sendCredentials_WhenUserExists_ShouldSendEmailAndReturnTrue() {
        // Arrange
        String email = "test@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode(anyString())).thenReturn("newEncodedPassword");
        doNothing().when(emailService).sendCredentialsEmail(anyString(), anyString(), anyString());

        // Act
        boolean result = userService.sendCredentials(email);

        // Assert
        assertThat(result).isTrue();
        verify(userRepository).findByEmail(email);
        verify(userRepository).save(any(User.class));
        verify(emailService).sendCredentialsEmail(eq(email), eq("testuser"), anyString());
    }

    @Test
    void sendCredentials_WhenUserDoesNotExist_ShouldReturnFalse() {
        // Arrange
        String email = "nonexistent@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        // Act
        boolean result = userService.sendCredentials(email);

        // Assert
        assertThat(result).isFalse();
        verify(userRepository).findByEmail(email);
        verify(userRepository, never()).save(any(User.class));
        verify(emailService, never()).sendCredentialsEmail(anyString(), anyString(), anyString());
    }

    @Test
    void existsByUsername_WhenUserExists_ShouldReturnTrue() {
        // Arrange
        when(userRepository.existsByUsername("testuser")).thenReturn(true);

        // Act
        boolean result = userService.existsByUsername("testuser");

        // Assert
        assertThat(result).isTrue();
        verify(userRepository).existsByUsername("testuser");
    }

    @Test
    void existsByUsername_WhenUserDoesNotExist_ShouldReturnFalse() {
        // Arrange
        when(userRepository.existsByUsername("nonexistent")).thenReturn(false);

        // Act
        boolean result = userService.existsByUsername("nonexistent");

        // Assert
        assertThat(result).isFalse();
        verify(userRepository).existsByUsername("nonexistent");
    }

    @Test
    void existsByEmail_WhenEmailExists_ShouldReturnTrue() {
        // Arrange
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        // Act
        boolean result = userService.existsByEmail("test@example.com");

        // Assert
        assertThat(result).isTrue();
        verify(userRepository).existsByEmail("test@example.com");
    }

    @Test
    void existsByEmail_WhenEmailDoesNotExist_ShouldReturnFalse() {
        // Arrange
        when(userRepository.existsByEmail("nonexistent@example.com")).thenReturn(false);

        // Act
        boolean result = userService.existsByEmail("nonexistent@example.com");

        // Assert
        assertThat(result).isFalse();
        verify(userRepository).existsByEmail("nonexistent@example.com");
    }

    @Test
    void findByUsername_WhenUserExists_ShouldReturnUser() {
        // Arrange
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        // Act
        Optional<User> result = userService.findByUsername("testuser");

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(testUser);
        verify(userRepository).findByUsername("testuser");
    }

    @Test
    void findByUsername_WhenUserDoesNotExist_ShouldReturnEmpty() {
        // Arrange
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        // Act
        Optional<User> result = userService.findByUsername("nonexistent");

        // Assert
        assertThat(result).isEmpty();
        verify(userRepository).findByUsername("nonexistent");
    }

    @Test
    void findById_WhenUserExists_ShouldReturnUser() {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // Act
        Optional<User> result = userService.findById(1L);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(testUser);
        verify(userRepository).findById(1L);
    }

    @Test
    void findById_WhenUserDoesNotExist_ShouldReturnEmpty() {
        // Arrange
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        // Act
        Optional<User> result = userService.findById(99L);

        // Assert
        assertThat(result).isEmpty();
        verify(userRepository).findById(99L);
    }
}