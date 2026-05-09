package com.psl.oms.repository;

import com.psl.oms.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * OrderItemRepository – data-access interface for {@link OrderItem} entities.
 *
 * <p>Replaces the original {@code OrderItemDAO} class.
 *
 * <p>Most OrderItem operations are handled through the {@link PurchaseOrder} cascade
 * (saving/deleting an order cascades to its items).  This repository is used when
 * direct access to line items is needed — primarily for billing reports.
 */
@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /**
     * Fetches all line items for a specific order.
     *
     * <p>Used by the bill-generation service to build an itemised invoice.
     *
     * @param orderId the parent order's ID
     * @return list of order items (empty if the order has no items, or doesn't exist)
     */
    List<OrderItem> findByOrderId(Long orderId);

    /**
     * Fetches all line items for a specific customer across all their orders.
     *
     * <p>Useful for account-level reporting and customer billing history.
     *
     * @param customerId the customer's ID
     * @return flat list of all order items belonging to the customer's orders
     */
    @Query("""
        SELECT i FROM OrderItem i
        WHERE i.order.customer.id = :customerId
        """)
    List<OrderItem> findByCustomerId(@Param("customerId") Long customerId);
}
