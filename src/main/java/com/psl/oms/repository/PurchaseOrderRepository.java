package com.psl.oms.repository;

import com.psl.oms.entity.OrderStatus;
import com.psl.oms.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * PurchaseOrderRepository – data-access interface for {@link PurchaseOrder} entities.
 *
 * Replaces the original PurchaseOrderDAO, keeping the same business queries
 * but expressed in JPQL instead of raw JDBC SQL.
 */
@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    // ── Basic lookups ────────────────────────────────────────────────────

    /**
     * Fetches all orders placed by a specific customer.
     * Spring Data derives the query from the method name — no JPQL needed.
     *
     * @param customerId the customer's surrogate key
     * @return list of that customer's orders (may be empty)
     */
    List<PurchaseOrder> findByCustomerId(Long customerId);

    /**
     * Fetches orders placed within an inclusive date range.
     *
     * @param from start date (inclusive)
     * @param to   end date (inclusive)
     * @return orders placed between the two dates
     */
    List<PurchaseOrder> findByPlacedDateBetween(LocalDate from, LocalDate to);

    /**
     * Fetches orders placed on a specific date.
     *
     * @param date the exact placed date
     * @return orders placed on that date
     */
    List<PurchaseOrder> findByPlacedDate(LocalDate date);

    // ── Delayed order detection ──────────────────────────────────────────

    /**
     * Fetches all orders that missed their SLA ship date and are still PENDING.
     *
     * Original business rule: orders must ship within 4 days of placement.
     * shipDate = placedDate + 4 is set at creation time, so delayed orders
     * are simply PENDING orders where shipDate has already passed.
     *
     * @param status must be OrderStatus.PENDING (passed as parameter for testability)
     * @param today  today's date (passed as parameter so tests can control it)
     * @return list of delayed PENDING orders
     */
    @Query("""
        SELECT o FROM PurchaseOrder o
        WHERE o.status = :status
        AND o.shipDate < :today
        """)
    List<PurchaseOrder> findDelayedOrders(
        @Param("status") OrderStatus status,
        @Param("today") LocalDate today
    );

    // ── Reporting queries ────────────────────────────────────────────────

    /**
     * Returns the count of SHIPPED orders grouped by month and year.
     *
     *
     * Returns Object[] rows: [year (Integer), month (Integer), count (Long)].
     * ReportingService maps these to MonthlyOrderResponse DTOs.
     *
     * @param shippedStatus must be OrderStatus.SHIPPED
     * @return list of [year, month, count] arrays ordered chronologically
     */
    @Query("""
        SELECT YEAR(o.actualShipDate), MONTH(o.actualShipDate), COUNT(o)
        FROM PurchaseOrder o
        WHERE o.status = :shippedStatus
        AND o.actualShipDate IS NOT NULL
        GROUP BY YEAR(o.actualShipDate), MONTH(o.actualShipDate)
        ORDER BY YEAR(o.actualShipDate), MONTH(o.actualShipDate)
        """)
    List<Object[]> findMonthlyShippedOrderCounts(@Param("shippedStatus") OrderStatus shippedStatus);

    /**
     * Returns total revenue collected grouped by month and year.
     *
     * Revenue = SUM(orderItem.unitPrice * orderItem.quantity) per shipped order.
     * unitPrice is the snapshot value captured at order time.
     *
     * @param shippedStatus must be OrderStatus.SHIPPED
     * @return list of [year, month, revenue (BigDecimal)] arrays ordered chronologically
     */
    @Query("""
        SELECT YEAR(o.actualShipDate), MONTH(o.actualShipDate),
               SUM(i.unitPrice * i.quantity)
        FROM PurchaseOrder o
        JOIN o.orderItems i
        WHERE o.status = :shippedStatus
        AND o.actualShipDate IS NOT NULL
        GROUP BY YEAR(o.actualShipDate), MONTH(o.actualShipDate)
        ORDER BY YEAR(o.actualShipDate), MONTH(o.actualShipDate)
        """)
    List<Object[]> findMonthlyRevenue(@Param("shippedStatus") OrderStatus shippedStatus);

    /**
     * Counts the total number of orders placed by a specific customer.
     *
     * Used by ReportingService.getTopCustomer() to get the order count
     * without loading the LAZY Customer.orders collection.
     * Spring Data derives the COUNT query from the method name.
     *
     * @param customerId the customer's surrogate key
     * @return total number of orders placed by this customer
     */
    long countByCustomerId(Long customerId);
}