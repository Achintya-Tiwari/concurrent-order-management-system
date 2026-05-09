package com.psl.oms.controller;

import com.psl.oms.dto.response.MonthlyOrderResponse;
import com.psl.oms.dto.response.MonthlyRevenueResponse;
import com.psl.oms.dto.response.TopCustomerResponse;
import com.psl.oms.service.ReportingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ReportController — REST endpoints for analytical reporting.
 *
 * All endpoints are read-only (GET). No request body or path variables needed —
 * these queries aggregate across the entire dataset.
 *
 * Migrated from the original project's ReportingService console menu options
 * (options 11–14 in OMSTester.java).
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Reports", description = "Analytical and business intelligence reports")
public class ReportController {

    private final ReportingService reportingService;

    /**
     * GET /api/reports/monthly-orders
     * Returns count of shipped orders grouped by month and year.
     *
     * Migrated from: option 11 — "Month-wise total number of orders shipped"
     */
    @GetMapping("/monthly-orders")
    @Operation(summary = "Monthly shipped order counts")
    public List<MonthlyOrderResponse> getMonthlyOrders() {
        return reportingService.getMonthlyOrderCounts();
    }

    /**
     * GET /api/reports/monthly-revenue
     * Returns total revenue collected from shipped orders grouped by month and year.
     *
     * Migrated from: option 12 — "Month-wise total amount collected"
     */
    @GetMapping("/monthly-revenue")
    @Operation(summary = "Monthly revenue from shipped orders")
    public List<MonthlyRevenueResponse> getMonthlyRevenue() {
        return reportingService.getMonthlyRevenue();
    }

    /**
     * GET /api/reports/top-customer
     * Returns the customer who has placed the most orders.
     *
     * Migrated from: option 13 — "Customer with maximum number of orders"
     * Returns HTTP 404 if no orders have been placed yet.
     */
    @GetMapping("/top-customer")
    @Operation(summary = "Customer with the most orders")
    public TopCustomerResponse getTopCustomer() {
        return reportingService.getTopCustomer();
    }
}
