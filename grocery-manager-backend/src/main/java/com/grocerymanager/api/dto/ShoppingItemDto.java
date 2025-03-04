package com.grocerymanager.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShoppingItemDto {
    private Long id;

    @NotBlank
    @Size(max = 255)
    private String name;

    @NotNull
    @Positive
    private Double quantity;

    @NotBlank
    @Size(max = 50)
    private String unitType;

    private boolean checked;

    private int sortIndex;

    private Long shoppingListId;

    private String syncId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime lastSynced;

    private Long version;
}