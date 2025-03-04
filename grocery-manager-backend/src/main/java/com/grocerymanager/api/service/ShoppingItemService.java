package com.grocerymanager.api.service;

import com.grocerymanager.api.dto.ShoppingItemDto;
import com.grocerymanager.api.model.ShoppingItem;
import com.grocerymanager.api.model.ShoppingList;
import com.grocerymanager.api.model.User;
import com.grocerymanager.api.repository.ShoppingItemRepository;
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
public class ShoppingItemService {

    @Autowired
    private ShoppingItemRepository itemRepository;

    @Autowired
    private ShoppingListRepository listRepository;

    @Transactional(readOnly = true)
    public List<ShoppingItemDto> getAllItemsByListId(Long listId, User user) {
        Optional<ShoppingList> list = listRepository.findByIdAndUser(listId, user);

        return list.map(l ->
                itemRepository.findAllByShoppingListOrderBySortIndexAsc(l)
                        .stream()
                        .map(this::convertToDto)
                        .collect(Collectors.toList())
        ).orElse(List.of());
    }

    @Transactional(readOnly = true)
    public Optional<ShoppingItemDto> getItemById(Long id, User user) {
        Optional<ShoppingItem> item = itemRepository.findById(id);

        return item.filter(i -> i.getShoppingList().getUser().getId().equals(user.getId()))
                .map(this::convertToDto);
    }

    @Transactional
    public Optional<ShoppingItemDto> createItem(ShoppingItemDto itemDto, User user) {
        return listRepository.findByIdAndUser(itemDto.getShoppingListId(), user)
                .map(list -> {
                    ShoppingItem item = new ShoppingItem();
                    item.setName(itemDto.getName());
                    item.setQuantity(itemDto.getQuantity());
                    item.setUnitType(itemDto.getUnitType());
                    item.setChecked(itemDto.isChecked());
                    item.setSortIndex(itemDto.getSortIndex());
                    item.setShoppingList(list);
                    item.setSyncId(UUID.randomUUID().toString());

                    LocalDateTime now = LocalDateTime.now();
                    item.setCreatedAt(now);
                    item.setUpdatedAt(now);
                    item.setLastSynced(now);

                    return convertToDto(itemRepository.save(item));
                });
    }

    @Transactional
    public Optional<ShoppingItemDto> updateItem(Long id, ShoppingItemDto itemDto, User user) {
        Optional<ShoppingItem> optionalItem = itemRepository.findById(id);

        return optionalItem.filter(i -> i.getShoppingList().getUser().getId().equals(user.getId()))
                .map(item -> {
                    item.setName(itemDto.getName());
                    item.setQuantity(itemDto.getQuantity());
                    item.setUnitType(itemDto.getUnitType());
                    item.setChecked(itemDto.isChecked());
                    item.setSortIndex(itemDto.getSortIndex());
                    item.setUpdatedAt(LocalDateTime.now());
                    item.setLastSynced(LocalDateTime.now());

                    return convertToDto(itemRepository.save(item));
                });
    }

    @Transactional
    public boolean deleteItem(Long id, User user) {
        Optional<ShoppingItem> optionalItem = itemRepository.findById(id);

        return optionalItem.filter(i -> i.getShoppingList().getUser().getId().equals(user.getId()))
                .map(item -> {
                    itemRepository.delete(item);
                    return true;
                })
                .orElse(false);
    }

    public ShoppingItemDto convertToDto(ShoppingItem item) {
        ShoppingItemDto dto = new ShoppingItemDto();
        dto.setId(item.getId());
        dto.setName(item.getName());
        dto.setQuantity(item.getQuantity());
        dto.setUnitType(item.getUnitType());
        dto.setChecked(item.isChecked());
        dto.setSortIndex(item.getSortIndex());
        dto.setShoppingListId(item.getShoppingList().getId());
        dto.setSyncId(item.getSyncId());
        dto.setCreatedAt(item.getCreatedAt());
        dto.setUpdatedAt(item.getUpdatedAt());
        dto.setLastSynced(item.getLastSynced());
        dto.setVersion(item.getVersion());
        return dto;
    }

    public ShoppingItem convertToEntity(ShoppingItemDto dto, ShoppingList list) {
        ShoppingItem entity = new ShoppingItem();
        entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setQuantity(dto.getQuantity());
        entity.setUnitType(dto.getUnitType());
        entity.setChecked(dto.isChecked());
        entity.setSortIndex(dto.getSortIndex());
        entity.setShoppingList(list);
        entity.setSyncId(dto.getSyncId());
        entity.setCreatedAt(dto.getCreatedAt());
        entity.setUpdatedAt(dto.getUpdatedAt());
        entity.setLastSynced(dto.getLastSynced());
        return entity;
    }
}