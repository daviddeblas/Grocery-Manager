package com.grocerymanager.api.service;

import com.grocerymanager.api.dto.ShoppingListDto;
import com.grocerymanager.api.model.ShoppingList;
import com.grocerymanager.api.model.User;
import com.grocerymanager.api.repository.ShoppingListRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ShoppingListService {

    @Autowired
    private ShoppingListRepository shoppingListRepository;

    @Transactional(readOnly = true)
    public List<ShoppingListDto> getAllListsByUser(User user) {
        return shoppingListRepository.findAllByUser(user)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<ShoppingListDto> getListById(Long id, User user) {
        return shoppingListRepository.findByIdAndUser(id, user)
                .map(this::convertToDto);
    }

    @Transactional
    public ShoppingListDto createList(ShoppingListDto listDto, User user) {
        ShoppingList list = new ShoppingList();
        list.setName(listDto.getName());
        list.setUser(user);
        list.setSyncId(UUID.randomUUID().toString());

        LocalDateTime now = LocalDateTime.now();
        list.setCreatedAt(now);
        list.setUpdatedAt(now);
        list.setLastSynced(now);

        ShoppingList savedList = shoppingListRepository.save(list);
        return convertToDto(savedList);
    }

    @Transactional
    public Optional<ShoppingListDto> updateList(Long id, ShoppingListDto listDto, User user) {
        return shoppingListRepository.findByIdAndUser(id, user)
                .map(list -> {
                    list.setName(listDto.getName());
                    list.setUpdatedAt(LocalDateTime.now());
                    list.setLastSynced(LocalDateTime.now());
                    return convertToDto(shoppingListRepository.save(list));
                });
    }

    @Transactional
    public boolean deleteList(Long id, User user) {
        return shoppingListRepository.findByIdAndUser(id, user)
                .map(list -> {
                    shoppingListRepository.delete(list);
                    return true;
                })
                .orElse(false);
    }

    public ShoppingListDto convertToDto(ShoppingList list) {
        ShoppingListDto dto = new ShoppingListDto();
        dto.setId(list.getId());
        dto.setName(list.getName());
        dto.setSyncId(list.getSyncId());
        dto.setCreatedAt(list.getCreatedAt());
        dto.setUpdatedAt(list.getUpdatedAt());
        dto.setLastSynced(list.getLastSynced());
        dto.setVersion(list.getVersion());
        return dto;
    }

    public ShoppingList convertToEntity(ShoppingListDto dto, User user) {
        ShoppingList entity = new ShoppingList();
        entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setSyncId(dto.getSyncId());
        entity.setUser(user);
        entity.setCreatedAt(dto.getCreatedAt());
        entity.setUpdatedAt(dto.getUpdatedAt());
        entity.setLastSynced(dto.getLastSynced());
        return entity;
    }
}