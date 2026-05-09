package com.psl.oms.entity;

/**
 * OrderStatus — lifecycle states of a PurchaseOrder.
 *
 * PENDING  → order placed, awaiting dispatch.
 * SHIPPED  → order dispatched to customer (terminal state).
 *
 * Stored as VARCHAR in the DB via @Enumerated(EnumType.STRING).
 */
public enum OrderStatus {
    PENDING,
    SHIPPED
}
