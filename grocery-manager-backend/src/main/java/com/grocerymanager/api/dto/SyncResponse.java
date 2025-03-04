package com.grocerymanager.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SyncResponse {
    private LocalDateTime serverTimestamp;
    private List<ShoppingListDto> shoppingLists;
    private List<ShoppingItemDto> shoppingItems;
    private List<StoreLocationDto> storeLocations;
}