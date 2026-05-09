package com.psl.oms.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * PurchaseOrder — a transaction record linking a Customer to a set of OrderItems.
 *
 * Business rules:
 *   - shipDate = placedDate + 4 days (SLA deadline, set at creation time).
 *   - Status starts as PENDING and transitions to SHIPPED when dispatched.
 *   - An order where shipDate < today AND status = PENDING is "delayed".
 */
@Entity
@Table(name = "purchase_order")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"customer", "orderItems"})
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long id;

    /** The customer who placed this order. FK on this table (owning side). */
    @NotNull(message = "Order must be associated with a customer")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_order_customer"))
    private Customer customer;

    @NotNull
    @Column(name = "placed_date", nullable = false)
    private LocalDate placedDate;

    /** SLA deadline: placedDate + 4 days. */
    @NotNull
    @Column(name = "ship_date", nullable = false)
    private LocalDate shipDate;

    /** Actual dispatch date — null while PENDING, set when shipped. */
    @Column(name = "actual_ship_date")
    private LocalDate actualShipDate;

    /** Stored as VARCHAR via EnumType.STRING — human-readable in the DB. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status",columnDefinition = "VARCHAR(20)", nullable = false, length = 20)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    /** CascadeType.ALL — saving/deleting an order cascades to its items. */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<OrderItem> orderItems = new ArrayList<>();

    /** Adds an item and wires the back-reference. Always use this over getOrderItems().add(). */
    public void addOrderItem(OrderItem item) {
        orderItems.add(item);
        item.setOrder(this);
    }

    public void removeOrderItem(OrderItem item) {
        orderItems.remove(item);
        item.setOrder(null);
    }
}
