package com.psl.oms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MonthlyOrderResponse — one row in the GET /api/reports/monthly-orders response.
 *
 * Migrated from the original ReportingService.getMonthWiseTotalOrdersShipped().
 * Each instance represents a single month's shipped-order count.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyOrderResponse {

    /** Calendar year (e.g. 2024). */
    private Integer year;

    /** Calendar month, 1 = January … 12 = December. */
    private Integer month;

    /** Human-readable month name (e.g. "JANUARY") — added for API readability. */
    private String monthName;

    /** Total number of orders shipped in this month. */
    private Long orderCount;
}
