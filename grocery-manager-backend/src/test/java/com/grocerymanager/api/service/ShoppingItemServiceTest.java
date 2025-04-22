package com.grocerymanager.api.service;

import com.grocerymanager.api.dto.ShoppingItemDto;
import com.grocerymanager.api.model.ShoppingItem;
import com.grocerymanager.api.model.ShoppingList;
import com.grocerymanager.api.model.User;
import com.grocerymanager.api.repository.ShoppingItemRepository;
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
public class ShoppingItemServiceTest {

    @Mock
    private ShoppingItemRepository itemRepository;

    @Mock
    private ShoppingListRepository listRepository;

    @InjectMocks
    private ShoppingItemService itemService;

    private User testUser;
    private ShoppingList testList;
    private ShoppingItem testItem;
    private ShoppingItemDto testItemDto;

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

        // Create a test shopping item
        testItem = new ShoppingItem();
        testItem.setId(1L);
        testItem.setName("Test Item");
        testItem.setQuantity(2.0);
        testItem.setUnitType("kg");
        testItem.setChecked(false);
        testItem.setSortIndex(0);
        testItem.setShoppingList(testList);
        testItem.setSyncId(UUID.randomUUID().toString());
        testItem.setCreatedAt(LocalDateTime.now());
        testItem.setUpdatedAt(LocalDateTime.now());

        // Create a test DTO
        testItemDto = new ShoppingItemDto();
        testItemDto.setId(1L);
        testItemDto.setName("Test Item");
        testItemDto.setQuantity(2.0);
        testItemDto.setUnitType("kg");
        testItemDto.setChecked(false);
        testItemDto.setSortIndex(0);
        testItemDto.setShoppingListId(1L);
        testItemDto.setSyncId(testItem.getSyncId());
        testItemDto.setCreatedAt(testItem.getCreatedAt());
        testItemDto.setUpdatedAt(testItem.getUpdatedAt());
    }

    @Test
    void getAllItemsByListId_ShouldReturnAllItems() {
        // Arrange
        when(listRepository.findByIdAndUser(1L, testUser))
                .thenReturn(Optional.of(testList));
        when(itemRepository.findAllByShoppingListOrderBySortIndexAsc(testList))
                .thenReturn(Arrays.asList(testItem));

        // Act
        List<ShoppingItemDto> result = itemService.getAllItemsByListId(1L, testUser);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo(testItem.getName());
        assertThat(result.get(0).getSyncId()).isEqualTo(testItem.getSyncId());
        verify(listRepository, times(1)).findByIdAndUser(1L, testUser);
        verify(itemRepository, times(1)).findAllByShoppingListOrderBySortIndexAsc(testList);
    }

    @Test
    void getAllItemsByListId_WhenListDoesNotExist_ShouldReturnEmptyList() {
        // Arrange
        when(listRepository.findByIdAndUser(99L, testUser))
                .thenReturn(Optional.empty());

        // Act
        List<ShoppingItemDto> result = itemService.getAllItemsByListId(99L, testUser);

        // Assert
        assertThat(result).isEmpty();
        verify(listRepository, times(1)).findByIdAndUser(99L, testUser);
        verify(itemRepository, never()).findAllByShoppingListOrderBySortIndexAsc(any());
    }

    @Test
    void getItemById_WhenItemExists_ShouldReturnItem() {
        // Arrange
        when(itemRepository.findById(1L))
                .thenReturn(Optional.of(testItem));

        // Act
        Optional<ShoppingItemDto> result = itemService.getItemById(1L, testUser);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo(testItem.getName());
        assertThat(result.get().getSyncId()).isEqualTo(testItem.getSyncId());
        verify(itemRepository, times(1)).findById(1L);
    }

    @Test
    void getItemById_WhenItemDoesNotExist_ShouldReturnEmpty() {
        // Arrange
        when(itemRepository.findById(99L))
                .thenReturn(Optional.empty());

        // Act
        Optional<ShoppingItemDto> result = itemService.getItemById(99L, testUser);

        // Assert
        assertThat(result).isEmpty();
        verify(itemRepository, times(1)).findById(99L);
    }

    @Test
    void getItemById_WhenItemBelongsToDifferentUser_ShouldReturnEmpty() {
        // Arrange
        User differentUser = new User();
        differentUser.setId(2L);

        ShoppingList listOfDifferentUser = new ShoppingList();
        listOfDifferentUser.setId(2L);
        listOfDifferentUser.setUser(differentUser);

        ShoppingItem itemOfDifferentUser = new ShoppingItem();
        itemOfDifferentUser.setId(1L);
        itemOfDifferentUser.setShoppingList(listOfDifferentUser);

        when(itemRepository.findById(1L))
                .thenReturn(Optional.of(itemOfDifferentUser));

        // Act
        Optional<ShoppingItemDto> result = itemService.getItemById(1L, testUser);

        // Assert
        assertThat(result).isEmpty();
        verify(itemRepository, times(1)).findById(1L);
    }

    @Test
    void createItem_WhenListExists_ShouldCreateAndReturnItem() {
        // Arrange
        ShoppingItemDto inputDto = new ShoppingItemDto();
        inputDto.setName("New Item");
        inputDto.setQuantity(1.0);
        inputDto.setUnitType("units");
        inputDto.setShoppingListId(1L);

        when(listRepository.findByIdAndUser(1L, testUser))
                .thenReturn(Optional.of(testList));
        when(itemRepository.save(any(ShoppingItem.class)))
                .thenAnswer(invocation -> {
                    ShoppingItem savedItem = invocation.getArgument(0);
                    savedItem.setId(2L);  // Simulate database assigning ID
                    return savedItem;
                });

        // Act
        Optional<ShoppingItemDto> result = itemService.createItem(inputDto, testUser);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("New Item");
        assertThat(result.get().getQuantity()).isEqualTo(1.0);
        assertThat(result.get().getUnitType()).isEqualTo("units");
        assertThat(result.get().getSyncId()).isNotNull();  // Should have generated a syncId
        verify(listRepository, times(1)).findByIdAndUser(1L, testUser);
        verify(itemRepository, times(1)).save(any(ShoppingItem.class));
    }

    @Test
    void createItem_WhenListDoesNotExist_ShouldReturnEmpty() {
        // Arrange
        ShoppingItemDto inputDto = new ShoppingItemDto();
        inputDto.setName("New Item");
        inputDto.setQuantity(1.0);
        inputDto.setUnitType("units");
        inputDto.setShoppingListId(99L);

        when(listRepository.findByIdAndUser(99L, testUser))
                .thenReturn(Optional.empty());

        // Act
        Optional<ShoppingItemDto> result = itemService.createItem(inputDto, testUser);

        // Assert
        assertThat(result).isEmpty();
        verify(listRepository, times(1)).findByIdAndUser(99L, testUser);
        verify(itemRepository, never()).save(any(ShoppingItem.class));
    }

    @Test
    void updateItem_WhenItemExists_ShouldUpdateAndReturnItem() {
        // Arrange
        ShoppingItemDto updateDto = new ShoppingItemDto();
        updateDto.setName("Updated Item");
        updateDto.setQuantity(3.0);
        updateDto.setUnitType("L");
        updateDto.setChecked(true);

        when(itemRepository.findById(1L))
                .thenReturn(Optional.of(testItem));
        when(itemRepository.save(any(ShoppingItem.class)))
                .thenReturn(testItem);

        // Act
        Optional<ShoppingItemDto> result = itemService.updateItem(1L, updateDto, testUser);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Updated Item");
        assertThat(result.get().getQuantity()).isEqualTo(3.0);
        assertThat(result.get().getUnitType()).isEqualTo("L");
        assertThat(result.get().isChecked()).isTrue();
        verify(itemRepository, times(1)).findById(1L);
        verify(itemRepository, times(1)).save(any(ShoppingItem.class));
    }

    @Test
    void updateItem_WhenItemDoesNotExist_ShouldReturnEmpty() {
        // Arrange
        ShoppingItemDto updateDto = new ShoppingItemDto();
        updateDto.setName("Updated Item");

        when(itemRepository.findById(99L))
                .thenReturn(Optional.empty());

        // Act
        Optional<ShoppingItemDto> result = itemService.updateItem(99L, updateDto, testUser);

        // Assert
        assertThat(result).isEmpty();
        verify(itemRepository, times(1)).findById(99L);
        verify(itemRepository, never()).save(any(ShoppingItem.class));
    }

    @Test
    void updateItem_WhenItemBelongsToDifferentUser_ShouldReturnEmpty() {
        // Arrange
        User differentUser = new User();
        differentUser.setId(2L);

        ShoppingList listOfDifferentUser = new ShoppingList();
        listOfDifferentUser.setId(2L);
        listOfDifferentUser.setUser(differentUser);

        ShoppingItem itemOfDifferentUser = new ShoppingItem();
        itemOfDifferentUser.setId(1L);
        itemOfDifferentUser.setShoppingList(listOfDifferentUser);

        ShoppingItemDto updateDto = new ShoppingItemDto();
        updateDto.setName("Updated Item");

        when(itemRepository.findById(1L))
                .thenReturn(Optional.of(itemOfDifferentUser));

        // Act
        Optional<ShoppingItemDto> result = itemService.updateItem(1L, updateDto, testUser);

        // Assert
        assertThat(result).isEmpty();
        verify(itemRepository, times(1)).findById(1L);
        verify(itemRepository, never()).save(any(ShoppingItem.class));
    }

    @Test
    void deleteItem_WhenItemExists_ShouldDeleteAndReturnTrue() {
        // Arrange
        when(itemRepository.findById(1L))
                .thenReturn(Optional.of(testItem));
        doNothing().when(itemRepository).delete(testItem);

        // Act
        boolean result = itemService.deleteItem(1L, testUser);

        // Assert
        assertThat(result).isTrue();
        verify(itemRepository, times(1)).findById(1L);
        verify(itemRepository, times(1)).delete(testItem);
    }

    @Test
    void deleteItem_WhenItemDoesNotExist_ShouldReturnFalse() {
        // Arrange
        when(itemRepository.findById(99L))
                .thenReturn(Optional.empty());

        // Act
        boolean result = itemService.deleteItem(99L, testUser);

        // Assert
        assertThat(result).isFalse();
        verify(itemRepository, times(1)).findById(99L);
        verify(itemRepository, never()).delete(any());
    }

    @Test
    void deleteItem_WhenItemBelongsToDifferentUser_ShouldReturnFalse() {
        // Arrange
        User differentUser = new User();
        differentUser.setId(2L);

        ShoppingList listOfDifferentUser = new ShoppingList();
        listOfDifferentUser.setId(2L);
        listOfDifferentUser.setUser(differentUser);

        ShoppingItem itemOfDifferentUser = new ShoppingItem();
        itemOfDifferentUser.setId(1L);
        itemOfDifferentUser.setShoppingList(listOfDifferentUser);

        when(itemRepository.findById(1L))
                .thenReturn(Optional.of(itemOfDifferentUser));

        // Act
        boolean result = itemService.deleteItem(1L, testUser);

        // Assert
        assertThat(result).isFalse();
        verify(itemRepository, times(1)).findById(1L);
        verify(itemRepository, never()).delete(any());
    }

    @Test
    void convertToDto_ShouldCorrectlyConvertEntityToDto() {
        // Act
        ShoppingItemDto result = itemService.convertToDto(testItem);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(testItem.getId());
        assertThat(result.getName()).isEqualTo(testItem.getName());
        assertThat(result.getQuantity()).isEqualTo(testItem.getQuantity());
        assertThat(result.getUnitType()).isEqualTo(testItem.getUnitType());
        assertThat(result.isChecked()).isEqualTo(testItem.isChecked());
        assertThat(result.getSortIndex()).isEqualTo(testItem.getSortIndex());
        assertThat(result.getShoppingListId()).isEqualTo(testItem.getShoppingList().getId());
        assertThat(result.getSyncId()).isEqualTo(testItem.getSyncId());
        assertThat(result.getCreatedAt()).isEqualTo(testItem.getCreatedAt());
        assertThat(result.getUpdatedAt()).isEqualTo(testItem.getUpdatedAt());
    }

    @Test
    void convertToEntity_ShouldCorrectlyConvertDtoToEntity() {
        // Act
        ShoppingItem result = itemService.convertToEntity(testItemDto, testList);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(testItemDto.getId());
        assertThat(result.getName()).isEqualTo(testItemDto.getName());
        assertThat(result.getQuantity()).isEqualTo(testItemDto.getQuantity());
        assertThat(result.getUnitType()).isEqualTo(testItemDto.getUnitType());
        assertThat(result.isChecked()).isEqualTo(testItemDto.isChecked());
        assertThat(result.getSortIndex()).isEqualTo(testItemDto.getSortIndex());
        assertThat(result.getShoppingList()).isEqualTo(testList);
        assertThat(result.getSyncId()).isEqualTo(testItemDto.getSyncId());
        assertThat(result.getCreatedAt()).isEqualTo(testItemDto.getCreatedAt());
        assertThat(result.getUpdatedAt()).isEqualTo(testItemDto.getUpdatedAt());
    }
}