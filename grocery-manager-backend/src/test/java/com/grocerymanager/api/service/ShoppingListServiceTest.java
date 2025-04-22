package com.grocerymanager.api.service;

import com.grocerymanager.api.dto.ShoppingListDto;
import com.grocerymanager.api.model.ShoppingList;
import com.grocerymanager.api.model.User;
import com.grocerymanager.api.repository.ShoppingListRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ShoppingListServiceTest {

    @Mock
    private ShoppingListRepository shoppingListRepository;

    @InjectMocks
    private ShoppingListService shoppingListService;

    private User testUser;
    private ShoppingList testList;
    private ShoppingListDto testListDto;

    @BeforeEach
    void setUp() {
        // Create test user
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");

        // Create a test shopping list
        testList = new ShoppingList();
        testList.setId(1L);
        testList.setName("Test Shopping List");
        testList.setUser(testUser);
        testList.setSyncId(UUID.randomUUID().toString());
        testList.setCreatedAt(LocalDateTime.now());
        testList.setUpdatedAt(LocalDateTime.now());

        // Create a test DTO
        testListDto = new ShoppingListDto();
        testListDto.setId(1L);
        testListDto.setName("Test Shopping List");
        testListDto.setSyncId(testList.getSyncId());
        testListDto.setCreatedAt(testList.getCreatedAt());
        testListDto.setUpdatedAt(testList.getUpdatedAt());
    }

    @Test
    void getAllListsByUser_ShouldReturnAllLists() {
        // Arrange
        when(shoppingListRepository.findAllByUser(testUser))
                .thenReturn(Arrays.asList(testList));

        // Act
        List<ShoppingListDto> result = shoppingListService.getAllListsByUser(testUser);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo(testList.getName());
        assertThat(result.get(0).getSyncId()).isEqualTo(testList.getSyncId());
        verify(shoppingListRepository, times(1)).findAllByUser(testUser);
    }

    @Test
    void getListById_WhenListExists_ShouldReturnList() {
        // Arrange
        when(shoppingListRepository.findByIdAndUser(1L, testUser))
                .thenReturn(Optional.of(testList));

        // Act
        Optional<ShoppingListDto> result = shoppingListService.getListById(1L, testUser);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo(testList.getName());
        assertThat(result.get().getSyncId()).isEqualTo(testList.getSyncId());
        verify(shoppingListRepository, times(1)).findByIdAndUser(1L, testUser);
    }

    @Test
    void getListById_WhenListDoesNotExist_ShouldReturnEmpty() {
        // Arrange
        when(shoppingListRepository.findByIdAndUser(99L, testUser))
                .thenReturn(Optional.empty());

        // Act
        Optional<ShoppingListDto> result = shoppingListService.getListById(99L, testUser);

        // Assert
        assertThat(result).isEmpty();
        verify(shoppingListRepository, times(1)).findByIdAndUser(99L, testUser);
    }

    @Test
    void createList_ShouldCreateAndReturnNewList() {
        // Arrange
        ShoppingListDto inputDto = new ShoppingListDto();
        inputDto.setName("New Shopping List");

        when(shoppingListRepository.save(any(ShoppingList.class)))
                .thenAnswer(invocation -> {
                    ShoppingList savedList = invocation.getArgument(0);
                    savedList.setId(2L);  // Simulate database assigning ID
                    return savedList;
                });

        // Act
        ShoppingListDto result = shoppingListService.createList(inputDto, testUser);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("New Shopping List");
        assertThat(result.getSyncId()).isNotNull();  // Should have generated a syncId
        verify(shoppingListRepository, times(1)).save(any(ShoppingList.class));
    }

    @Test
    void updateList_WhenListExists_ShouldUpdateAndReturnList() {
        // Arrange
        ShoppingListDto updateDto = new ShoppingListDto();
        updateDto.setName("Updated Shopping List");

        when(shoppingListRepository.findByIdAndUser(1L, testUser))
                .thenReturn(Optional.of(testList));
        when(shoppingListRepository.save(any(ShoppingList.class)))
                .thenReturn(testList);

        // Act
        Optional<ShoppingListDto> result = shoppingListService.updateList(1L, updateDto, testUser);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Updated Shopping List");
        verify(shoppingListRepository, times(1)).findByIdAndUser(1L, testUser);
        verify(shoppingListRepository, times(1)).save(any(ShoppingList.class));
    }

    @Test
    void updateList_WhenListDoesNotExist_ShouldReturnEmpty() {
        // Arrange
        ShoppingListDto updateDto = new ShoppingListDto();
        updateDto.setName("Updated Shopping List");

        when(shoppingListRepository.findByIdAndUser(99L, testUser))
                .thenReturn(Optional.empty());

        // Act
        Optional<ShoppingListDto> result = shoppingListService.updateList(99L, updateDto, testUser);

        // Assert
        assertThat(result).isEmpty();
        verify(shoppingListRepository, times(1)).findByIdAndUser(99L, testUser);
        verify(shoppingListRepository, never()).save(any(ShoppingList.class));
    }

    @Test
    void deleteList_WhenListExists_ShouldDeleteAndReturnTrue() {
        // Arrange
        when(shoppingListRepository.findByIdAndUser(1L, testUser))
                .thenReturn(Optional.of(testList));
        doNothing().when(shoppingListRepository).delete(testList);

        // Act
        boolean result = shoppingListService.deleteList(1L, testUser);

        // Assert
        assertThat(result).isTrue();
        verify(shoppingListRepository, times(1)).findByIdAndUser(1L, testUser);
        verify(shoppingListRepository, times(1)).delete(testList);
    }

    @Test
    void deleteList_WhenListDoesNotExist_ShouldReturnFalse() {
        // Arrange
        when(shoppingListRepository.findByIdAndUser(99L, testUser))
                .thenReturn(Optional.empty());

        // Act
        boolean result = shoppingListService.deleteList(99L, testUser);

        // Assert
        assertThat(result).isFalse();
        verify(shoppingListRepository, times(1)).findByIdAndUser(99L, testUser);
        verify(shoppingListRepository, never()).delete(any());
    }

    @Test
    void convertToDto_ShouldCorrectlyConvertEntityToDto() {
        // Act
        ShoppingListDto result = shoppingListService.convertToDto(testList);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(testList.getId());
        assertThat(result.getName()).isEqualTo(testList.getName());
        assertThat(result.getSyncId()).isEqualTo(testList.getSyncId());
        assertThat(result.getCreatedAt()).isEqualTo(testList.getCreatedAt());
        assertThat(result.getUpdatedAt()).isEqualTo(testList.getUpdatedAt());
    }

    @Test
    void convertToEntity_ShouldCorrectlyConvertDtoToEntity() {
        // Act
        ShoppingList result = shoppingListService.convertToEntity(testListDto, testUser);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(testListDto.getId());
        assertThat(result.getName()).isEqualTo(testListDto.getName());
        assertThat(result.getSyncId()).isEqualTo(testListDto.getSyncId());
        assertThat(result.getCreatedAt()).isEqualTo(testListDto.getCreatedAt());
        assertThat(result.getUpdatedAt()).isEqualTo(testListDto.getUpdatedAt());
        assertThat(result.getUser()).isEqualTo(testUser);
    }
}