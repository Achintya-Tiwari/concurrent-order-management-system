package com.psl.oms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * MonthlyRevenueResponse — one row in the GET /api/reports/monthly-revenue response.
 *
 * Migrated from the original ReportingService.getTotalAmountByMonth().
 * Revenue = sum of (quantity × unitPrice) for all shipped orders in the month.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyRevenueResponse {

    private Integer year;
    private Integer month;
    private String monthName;

    /** Total revenue collected from shipped orders in this month. */
    private BigDecimal totalRevenue;
}
