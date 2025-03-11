package com.grocerymanager.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeletedItemDto {
    private String syncId;
    private Long originalId;
    private String entityType;
    private LocalDateTime deletedAt;
}