package com.psl.oms.service;

import com.psl.oms.dto.response.MonthlyOrderResponse;
import com.psl.oms.dto.response.MonthlyRevenueResponse;
import com.psl.oms.dto.response.TopCustomerResponse;
import com.psl.oms.entity.Customer;
import com.psl.oms.entity.OrderStatus;
import com.psl.oms.exception.ResourceNotFoundException;
import com.psl.oms.repository.CustomerRepository;
import com.psl.oms.repository.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Month;
import java.util.List;

/**
 * ReportingService — read-only analytical queries.
 *
 * All methods run in a read-only transaction (class-level default).
 * Hibernate skips dirty checking, and the connection pool can route
 * reads to a replica if one is configured.
 *
 * The raw Object[] results from JPQL aggregate queries are mapped here
 * to strongly typed response DTOs. Controllers remain thin — they only
 * call service methods and return HTTP responses.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReportingService {

    private final PurchaseOrderRepository orderRepository;
    private final CustomerRepository customerRepository;

    /**
     * Returns the count of shipped orders grouped by month and year.
     *
     * Migrated from: ReportingService.getMonthWiseTotalOrdersShipped()
     *
     * The repository query returns Object[] rows: [year(int), month(int), count(long)].
     * Mapped here to MonthlyOrderResponse DTOs with a human-readable month name.
     *
     * @return list of monthly order counts, ordered chronologically
     */
    public List<MonthlyOrderResponse> getMonthlyOrderCounts() {
        log.debug("Fetching monthly shipped order counts");

        return orderRepository.findMonthlyShippedOrderCounts(OrderStatus.SHIPPED)
                .stream()
                .map(row -> MonthlyOrderResponse.builder()
                        .year(((Number) row[0]).intValue())
                        .month(((Number) row[1]).intValue())
                        .monthName(Month.of(((Number) row[1]).intValue()).name())
                        .orderCount(((Number) row[2]).longValue())
                        .build())
                .toList();
    }

    /**
     * Returns total revenue from shipped orders grouped by month and year.
     *
     * Migrated from: ReportingService.getTotalAmountByMonth()
     *
     * Revenue = SUM(orderItem.quantity × orderItem.unitPrice) per shipped order.
     * unitPrice is the snapshot value stored at order time — historically accurate
     * even if stock prices have changed since.
     *
     * @return list of monthly revenue totals, ordered chronologically
     */
    public List<MonthlyRevenueResponse> getMonthlyRevenue() {
        log.debug("Fetching monthly revenue");

        return orderRepository.findMonthlyRevenue(OrderStatus.SHIPPED)
                .stream()
                .map(row -> MonthlyRevenueResponse.builder()
                        .year(((Number) row[0]).intValue())
                        .month(((Number) row[1]).intValue())
                        .monthName(Month.of(((Number) row[1]).intValue()).name())
                        .totalRevenue(row[2] != null ? (BigDecimal) row[2] : BigDecimal.ZERO)
                        .build())
                .toList();
    }

    /**
     * Returns the customer who has placed the most orders.
     *
     * Migrated from: ReportingService.getCustomerWithMaxOrders()
     *
     *
     * @return the top customer's details and their total order count
     * @throws ResourceNotFoundException if no orders have been placed yet
     */
    public TopCustomerResponse getTopCustomer() {
        log.debug("Fetching top customer by order count");

        List<Customer> results = customerRepository.findTopCustomerByOrderCount(
                PageRequest.of(0, 1)
        );

        if (results.isEmpty()) {
            throw new ResourceNotFoundException("TopCustomer", "orders", "none placed yet");
        }

        Customer topCustomer = results.get(0);

        // Count orders via a simple repository call — avoids touching the LAZY collection.
        long orderCount = orderRepository.countByCustomerId(topCustomer.getId());

        return TopCustomerResponse.builder()
                .customerId(topCustomer.getId())
                .customerName(topCustomer.getName())
                .cellNumber(topCustomer.getCellNumber())
                .totalOrders(orderCount)
                .build();
    }
}
