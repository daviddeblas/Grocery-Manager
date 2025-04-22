package com.grocerymanager.api.service.sync;

import com.grocerymanager.api.dto.ShoppingListDto;
import com.grocerymanager.api.model.ShoppingList;
import com.grocerymanager.api.model.User;
import com.grocerymanager.api.repository.ShoppingListRepository;
import com.grocerymanager.api.service.ShoppingListService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ShoppingListSyncServiceTest {

    @Mock
    private ShoppingListRepository listRepository;

    @Mock
    private ShoppingListService listService;

    @InjectMocks
    private ShoppingListSyncService listSyncService;

    private User testUser;
    private ShoppingList testList;
    private ShoppingListDto testListDto;
    private LocalDateTime testSyncTime;

    @BeforeEach
    void setUp() {
        // Set up test user
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");

        // Set up test sync time
        testSyncTime = LocalDateTime.now();

        // Set up test shopping list
        testList = new ShoppingList();
        testList.setId(1L);
        testList.setName("Test Shopping List");
        testList.setSyncId("test-list-sync-id");
        testList.setUser(testUser);
        testList.setCreatedAt(testSyncTime.minusDays(1));
        testList.setUpdatedAt(testSyncTime.minusDays(1));

        // Set up test shopping list DTO
        testListDto = new ShoppingListDto();
        testListDto.setId(1L);
        testListDto.setName("Test Shopping List");
        testListDto.setSyncId("test-list-sync-id");
        testListDto.setCreatedAt(testSyncTime.minusDays(1));
        testListDto.setUpdatedAt(testSyncTime.minusDays(1));

        // Set up mock for convertToDto with lenient stubbing
        Mockito.lenient().when(listService.convertToDto(any(ShoppingList.class))).thenAnswer(invocation -> {
            ShoppingList list = invocation.getArgument(0);
            ShoppingListDto dto = new ShoppingListDto();
            dto.setId(list.getId());
            dto.setName(list.getName());
            dto.setSyncId(list.getSyncId());
            dto.setCreatedAt(list.getCreatedAt());
            dto.setUpdatedAt(list.getUpdatedAt());
            return dto;
        });
    }

    @Test
    void syncShoppingListsInNewTransaction_ShouldDelegateToSyncShoppingLists() {
        // Arrange
        List<ShoppingListDto> clientLists = Arrays.asList(testListDto);
        List<ShoppingListDto> expectedResult = Arrays.asList(testListDto);

        // Create a spy on the service to verify the delegation
        ShoppingListSyncService spyService = spy(listSyncService);
        doReturn(expectedResult).when(spyService).syncShoppingLists(clientLists, testUser, testSyncTime);

        // Act
        List<ShoppingListDto> result = spyService.syncShoppingListsInNewTransaction(clientLists, testUser, testSyncTime);

        // Assert
        verify(spyService).syncShoppingLists(clientLists, testUser, testSyncTime);
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    void syncShoppingLists_WithEmptyList_ShouldReturnEmptyList() {
        // Arrange
        List<ShoppingListDto> emptyList = new ArrayList<>();

        // Act
        List<ShoppingListDto> result = listSyncService.syncShoppingLists(emptyList, testUser, testSyncTime);

        // Assert
        assertThat(result).isEmpty();
        verify(listRepository, never()).existsBySyncIdAndUser(anyString(), any(User.class));
        verify(listRepository, never()).updateBySyncIdAndUser(anyString(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class), any(User.class));
        verify(listRepository, never()).findBySyncIdAndUser(anyString(), any(User.class));
        verify(listRepository, never()).save(any(ShoppingList.class));
    }

    @Test
    void syncShoppingLists_WithNullList_ShouldReturnEmptyList() {
        // Act
        List<ShoppingListDto> result = listSyncService.syncShoppingLists(null, testUser, testSyncTime);

        // Assert
        assertThat(result).isEmpty();
        verify(listRepository, never()).existsBySyncIdAndUser(anyString(), any(User.class));
        verify(listRepository, never()).updateBySyncIdAndUser(anyString(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class), any(User.class));
        verify(listRepository, never()).findBySyncIdAndUser(anyString(), any(User.class));
        verify(listRepository, never()).save(any(ShoppingList.class));
    }

    @Test
    void syncShoppingLists_WithExistingList_ShouldUpdateList() {
        // Arrange
        List<ShoppingListDto> clientLists = Arrays.asList(testListDto);
        when(listRepository.existsBySyncIdAndUser("test-list-sync-id", testUser)).thenReturn(true);
        when(listRepository.updateBySyncIdAndUser(anyString(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class), any(User.class))).thenReturn(1);
        when(listRepository.findBySyncIdAndUser("test-list-sync-id", testUser)).thenReturn(Optional.of(testList));

        // Act
        List<ShoppingListDto> result = listSyncService.syncShoppingLists(clientLists, testUser, testSyncTime);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSyncId()).isEqualTo("test-list-sync-id");
        verify(listRepository).updateBySyncIdAndUser("test-list-sync-id", "Test Shopping List", testSyncTime, testSyncTime, testUser);
        verify(listRepository).findBySyncIdAndUser("test-list-sync-id", testUser);
        verify(listService).convertToDto(testList);
    }

    @Test
    void syncShoppingLists_WithExistingListButUpdateFails_ShouldHandleConflict() {
        // Arrange
        List<ShoppingListDto> clientLists = Arrays.asList(testListDto);
        when(listRepository.existsBySyncIdAndUser("test-list-sync-id", testUser)).thenReturn(true);
        when(listRepository.updateBySyncIdAndUser(anyString(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class), any(User.class))).thenReturn(0);
        when(listRepository.findBySyncIdAndUser("test-list-sync-id", testUser)).thenReturn(Optional.of(testList));

        // Set client data to be newer
        testListDto.setUpdatedAt(testSyncTime);
        testList.setUpdatedAt(testSyncTime.minusDays(1));

        when(listRepository.save(any(ShoppingList.class))).thenReturn(testList);

        // Act
        List<ShoppingListDto> result = listSyncService.syncShoppingLists(clientLists, testUser, testSyncTime);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSyncId()).isEqualTo("test-list-sync-id");
        verify(listRepository).updateBySyncIdAndUser("test-list-sync-id", "Test Shopping List", testSyncTime, testSyncTime, testUser);
        verify(listRepository).findBySyncIdAndUser("test-list-sync-id", testUser);
        verify(listRepository).save(any(ShoppingList.class));
        verify(listService).convertToDto(testList);
    }

    @Test
    void syncShoppingLists_WithNewListWithSyncId_ShouldCreateList() {
        // Arrange
        ShoppingListDto newListDto = new ShoppingListDto();
        newListDto.setName("New List");
        newListDto.setSyncId("new-list-sync-id");

        List<ShoppingListDto> clientLists = Arrays.asList(newListDto);
        when(listRepository.existsBySyncIdAndUser("new-list-sync-id", testUser)).thenReturn(false);

        ShoppingList savedList = new ShoppingList();
        savedList.setId(2L);
        savedList.setName("New List");
        savedList.setSyncId("new-list-sync-id");
        savedList.setUser(testUser);
        savedList.setCreatedAt(testSyncTime);
        savedList.setUpdatedAt(testSyncTime);

        when(listRepository.save(any(ShoppingList.class))).thenReturn(savedList);

        // Act
        List<ShoppingListDto> result = listSyncService.syncShoppingLists(clientLists, testUser, testSyncTime);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSyncId()).isEqualTo("new-list-sync-id");
        assertThat(result.get(0).getName()).isEqualTo("New List");
        verify(listRepository).existsBySyncIdAndUser("new-list-sync-id", testUser);
        verify(listRepository, never()).updateBySyncIdAndUser(anyString(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class), any(User.class));
        verify(listRepository).save(any(ShoppingList.class));
        verify(listService).convertToDto(savedList);
    }

    @Test
    void syncShoppingLists_WithNewListWithoutSyncId_ShouldCreateListWithGeneratedSyncId() {
        // Arrange
        ShoppingListDto newListDto = new ShoppingListDto();
        newListDto.setName("New List Without SyncId");

        List<ShoppingListDto> clientLists = Arrays.asList(newListDto);

        ShoppingList savedList = new ShoppingList();
        savedList.setId(3L);
        savedList.setName("New List Without SyncId");
        savedList.setSyncId("generated-sync-id");
        savedList.setUser(testUser);
        savedList.setCreatedAt(testSyncTime);
        savedList.setUpdatedAt(testSyncTime);

        when(listRepository.save(any(ShoppingList.class))).thenReturn(savedList);

        // Act
        List<ShoppingListDto> result = listSyncService.syncShoppingLists(clientLists, testUser, testSyncTime);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("New List Without SyncId");
        verify(listRepository, never()).existsBySyncIdAndUser(anyString(), any(User.class));
        verify(listRepository, never()).updateBySyncIdAndUser(anyString(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class), any(User.class));
        verify(listRepository).save(any(ShoppingList.class));
        verify(listService).convertToDto(savedList);
    }

    @Test
    void syncShoppingLists_WhenRepositoryThrowsException_ShouldContinueWithOtherLists() {
        // Arrange
        ShoppingListDto list1 = new ShoppingListDto();
        list1.setName("List 1");
        list1.setSyncId("list1-sync-id");

        ShoppingListDto list2 = new ShoppingListDto();
        list2.setName("List 2");
        list2.setSyncId("list2-sync-id");

        List<ShoppingListDto> clientLists = Arrays.asList(list1, list2);

        when(listRepository.existsBySyncIdAndUser("list1-sync-id", testUser)).thenThrow(new RuntimeException("Test exception"));
        when(listRepository.existsBySyncIdAndUser("list2-sync-id", testUser)).thenReturn(true);
        when(listRepository.updateBySyncIdAndUser("list2-sync-id", "List 2", testSyncTime, testSyncTime, testUser)).thenReturn(1);

        ShoppingList savedList2 = new ShoppingList();
        savedList2.setId(2L);
        savedList2.setName("List 2");
        savedList2.setSyncId("list2-sync-id");
        savedList2.setUser(testUser);

        when(listRepository.findBySyncIdAndUser("list2-sync-id", testUser)).thenReturn(Optional.of(savedList2));

        // Act
        List<ShoppingListDto> result = listSyncService.syncShoppingLists(clientLists, testUser, testSyncTime);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSyncId()).isEqualTo("list2-sync-id");
        verify(listRepository).existsBySyncIdAndUser("list1-sync-id", testUser);
        verify(listRepository).existsBySyncIdAndUser("list2-sync-id", testUser);
        verify(listRepository).updateBySyncIdAndUser("list2-sync-id", "List 2", testSyncTime, testSyncTime, testUser);
        verify(listRepository).findBySyncIdAndUser("list2-sync-id", testUser);
        verify(listService).convertToDto(savedList2);
    }

    @Test
    void getChangedListsFromServer_WithLastSyncNull_ShouldReturnAllLists() {
        // Arrange
        List<ShoppingList> allLists = Arrays.asList(testList);
        when(listRepository.findAllByUser(testUser)).thenReturn(allLists);

        // Act
        List<ShoppingListDto> result = listSyncService.getChangedListsFromServer(testUser, null);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSyncId()).isEqualTo("test-list-sync-id");
        verify(listRepository).findAllByUser(testUser);
        verify(listRepository, never()).findByUserAndLastSyncedAfter(any(User.class), any(LocalDateTime.class));
        verify(listService).convertToDto(testList);
    }

    @Test
    void getChangedListsFromServer_WithLastSync_ShouldReturnOnlyChangedLists() {
        // Arrange
        LocalDateTime lastSync = testSyncTime.minusHours(1);
        List<ShoppingList> changedLists = Arrays.asList(testList);
        when(listRepository.findByUserAndLastSyncedAfter(testUser, lastSync)).thenReturn(changedLists);

        // Act
        List<ShoppingListDto> result = listSyncService.getChangedListsFromServer(testUser, lastSync);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSyncId()).isEqualTo("test-list-sync-id");
        verify(listRepository, never()).findAllByUser(any(User.class));
        verify(listRepository).findByUserAndLastSyncedAfter(testUser, lastSync);
        verify(listService).convertToDto(testList);
    }
}
