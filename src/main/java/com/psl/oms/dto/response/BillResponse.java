package com.psl.oms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * BillResponse — returned by GET /api/orders/{id}/bill.
 *
 * Replaces the original BillGenerator that wrote .txt files to disk.
 * The same invoice data is now returned as structured JSON — the client
 * can render, print, or store it however it needs.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillResponse {

    // ── Header ────────────────────────────────────────────────────────────
    private Long orderId;
    private LocalDate placedDate;
    private LocalDate actualShipDate;

    // ── Customer summary ──────────────────────────────────────────────────
    private Long customerId;
    private String customerName;
    private String customerAddress;
    private String customerCellNumber;

    // ── Line items ────────────────────────────────────────────────────────
    private List<OrderItemResponse> items;

    // ── Totals ────────────────────────────────────────────────────────────
    private BigDecimal grandTotal;
}
