package com.psl.oms.dto.response;

import com.psl.oms.entity.OrderStatus;
import com.psl.oms.entity.PurchaseOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * OrderResponse — outbound DTO for all /api/orders endpoints.
 *
 * Key design decisions:
 *   - customerName is denormalised so clients don't need a follow-up GET /api/customers/{id}.
 *   - totalAmount is computed from item subtotals — not stored in the DB.
 *   - delayed flag is computed at mapping time so clients don't implement date logic.
 *
 * IMPORTANT: from(order) must be called inside a transaction (or with items
 * already loaded) because it accesses the LAZY orderItems and stockItem associations.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {

    private Long id;
    private Long customerId;
    private String customerName;

    private LocalDate placedDate;
    private LocalDate shipDate;
    private LocalDate actualShipDate;
    private OrderStatus status;

    private List<OrderItemResponse> items;
    private BigDecimal totalAmount;

    /** true when status=PENDING and the SLA ship date has already passed today. */
    private boolean delayed;

    public static OrderResponse from(PurchaseOrder order) {
        List<OrderItemResponse> itemResponses = order.getOrderItems().stream()
                .map(OrderItemResponse::from)
                .toList();

        BigDecimal total = itemResponses.stream()
                .map(OrderItemResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        boolean isDelayed = order.getStatus() == OrderStatus.PENDING
                && order.getShipDate().isBefore(LocalDate.now());

        return OrderResponse.builder()
                .id(order.getId())
                .customerId(order.getCustomer().getId())
                .customerName(order.getCustomer().getName())
                .placedDate(order.getPlacedDate())
                .shipDate(order.getShipDate())
                .actualShipDate(order.getActualShipDate())
                .status(order.getStatus())
                .items(itemResponses)
                .totalAmount(total)
                .delayed(isDelayed)
                .build();
    }
}
