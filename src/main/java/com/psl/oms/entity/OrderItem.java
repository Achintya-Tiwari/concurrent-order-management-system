package com.psl.oms.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

/**
 * OrderItem — a single line item within a PurchaseOrder.
 *
 * Represents "X units of StockItem Y in Order Z".
 * unitPrice is a snapshot captured at purchase time — if the product price
 * changes later, the historical bill still reflects the original price.
 *
 * OrderItem is the owning side of both its FK relationships (order_id, stock_item_id).
 */
@Entity
@Table(name = "order_item")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"order", "stockItem"})
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_item_id")
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_order_item_order"))
    private PurchaseOrder order;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_item_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_order_item_stock_item"))
    private StockItem stockItem;

    @Min(value = 1, message = "Quantity must be at least 1")
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    /** Snapshot of the stock item's price at order time. BigDecimal for monetary safety. */
    @NotNull
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    /** Calculates unitPrice × quantity. Transient — not stored in the DB. */
    @Transient
    public BigDecimal getSubtotal() {
        if (unitPrice == null || quantity == null) return BigDecimal.ZERO;
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
