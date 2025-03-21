package com.grocerymanager.api.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "shopping_items", uniqueConstraints = {
        @UniqueConstraint(name = "uk_shopping_items_sync_id", columnNames = "sync_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShoppingItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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

    private boolean checked = false;

    private int sortIndex;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shopping_list_id", nullable = false)
    @ToString.Exclude
    private ShoppingList shoppingList;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Column(name = "sync_id", unique = true)
    private String syncId;

    private LocalDateTime lastSynced;

    // For managing synchronization conflicts
    @Version
    private Long version;
}