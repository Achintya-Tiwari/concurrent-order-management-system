package com.psl.oms.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

/**
 * EntityRelationshipTest – unit tests for entity domain logic.
 *
 * <p>These are pure Java unit tests — no Spring context, no database.
 * They verify that entity helper methods and derived calculations
 * work correctly in isolation.
 *
 * <p>Fast to run: no I/O, no application startup.
 */
class EntityRelationshipTest {

    // ── OrderItem.getSubtotal() ──────────────────────────────────────────

    @Test
    @DisplayName("OrderItem subtotal is unitPrice × quantity")
    void orderItem_subtotal_calculatesCorrectly() {
        OrderItem item = OrderItem.builder()
            .unitPrice(new BigDecimal("19.99"))
            .quantity(3)
            .build();

        BigDecimal expected = new BigDecimal("59.97");
        assertThat(item.getSubtotal()).isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("OrderItem subtotal returns zero when unitPrice is null")
    void orderItem_subtotal_returnsZero_whenUnitPriceNull() {
        OrderItem item = OrderItem.builder().quantity(2).build();
        assertThat(item.getSubtotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("OrderItem subtotal returns zero when quantity is null")
    void orderItem_subtotal_returnsZero_whenQuantityNull() {
        OrderItem item = OrderItem.builder().unitPrice(new BigDecimal("10.00")).build();
        assertThat(item.getSubtotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ── PurchaseOrder helper methods ─────────────────────────────────────

    @Test
    @DisplayName("addOrderItem wires both sides of the relationship")
    void purchaseOrder_addOrderItem_wiresBothSides() {
        PurchaseOrder order = PurchaseOrder.builder()
            .placedDate(LocalDate.now())
            .shipDate(LocalDate.now().plusDays(4))
            .status(OrderStatus.PENDING)
            .build();

        OrderItem item = new OrderItem();
        order.addOrderItem(item);

        assertThat(order.getOrderItems()).containsExactly(item);
        assertThat(item.getOrder()).isSameAs(order);
    }

    @Test
    @DisplayName("removeOrderItem clears both sides of the relationship")
    void purchaseOrder_removeOrderItem_clearsBothSides() {
        PurchaseOrder order = PurchaseOrder.builder()
            .placedDate(LocalDate.now())
            .shipDate(LocalDate.now().plusDays(4))
            .status(OrderStatus.PENDING)
            .build();

        OrderItem item = new OrderItem();
        order.addOrderItem(item);
        order.removeOrderItem(item);

        assertThat(order.getOrderItems()).isEmpty();
        assertThat(item.getOrder()).isNull();
    }

    // ── OrderStatus enum ─────────────────────────────────────────────────

    @Test
    @DisplayName("OrderStatus default on PurchaseOrder is PENDING")
    void purchaseOrder_defaultStatus_isPending() {
        PurchaseOrder order = new PurchaseOrder();
        // Builder.Default ensures even no-arg constructor initialises status
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    // ── Customer builder ─────────────────────────────────────────────────

    @Test
    @DisplayName("Customer builder produces correct field values")
    void customer_builder_setsFieldsCorrectly() {
        Customer customer = Customer.builder()
            .name("Jane Doe")
            .cellNumber("9876543210")
            .address("123 Main St")
            .build();

        assertThat(customer.getName()).isEqualTo("Jane Doe");
        assertThat(customer.getCellNumber()).isEqualTo("9876543210");
        assertThat(customer.getOrders()).isNotNull().isEmpty();
    }
}
