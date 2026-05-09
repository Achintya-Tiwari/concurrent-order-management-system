package com.psl.oms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TopCustomerResponse — returned by GET /api/reports/top-customer.
 *
 * Migrated from the original ReportingService.getCustomerWithMaxOrders().
 * Identifies the customer who has placed the most orders overall.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopCustomerResponse {

    private Long customerId;
    private String customerName;
    private String cellNumber;

    /** Total number of orders this customer has placed. */
    private Long totalOrders;
}
