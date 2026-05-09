package com.psl.oms.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * StockItem — a product in the OMS catalogue.
 *
 * Duplicate names are prevented by a unique constraint.
 * The quantity field tracks available inventory and is decremented atomically
 * when an order is placed (via StockItemRepository.decrementQuantity).
 */
@Entity
@Table(name = "stock_item",
       uniqueConstraints = @UniqueConstraint(name = "uq_stock_item_name", columnNames = "name"))
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "orderItems")
public class StockItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stock_item_id")
    private Long id;

    @NotBlank(message = "Stock item name must not be blank")
    @Size(max = 100)
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** Unit price. BigDecimal for monetary safety — avoids floating-point rounding. */
    @NotNull(message = "Price must not be null")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than zero")
    @Digits(integer = 8, fraction = 2)
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /** Available stock. Default 0 = out of stock until restocked. */
    @Min(value = 0, message = "Quantity cannot be negative")
    @Column(name = "quantity", nullable = false)
    @Builder.Default
    private Integer quantity = 0;

    /** Inverse side — no cascade here (cascade only belongs on the owning side). */
    @OneToMany(mappedBy = "stockItem", fetch = FetchType.LAZY)
    @Builder.Default
    private List<OrderItem> orderItems = new ArrayList<>();
}
