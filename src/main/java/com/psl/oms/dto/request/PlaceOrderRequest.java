package com.psl.oms.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * PlaceOrderRequest — inbound payload for POST /api/orders.
 *
 * Example JSON:
 * {
 *   "customerId": 1,
 *   "items": [
 *     { "stockItemId": 3, "quantity": 2 },
 *     { "stockItemId": 7, "quantity": 1 }
 *   ]
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaceOrderRequest {

    @NotNull(message = "Customer ID is required")
    @Positive(message = "Customer ID must be a positive number")
    private Long customerId;

    /**
     * @Valid cascades Bean Validation into each OrderItemRequest element.
     * @NotEmpty ensures at least one item is present.
     */
    @NotEmpty(message = "Order must contain at least one item")
    @Valid
    private List<OrderItemRequest> items;

    /**
     * OrderItemRequest — a single line item nested inside PlaceOrderRequest.
     * Declared static so it can be referenced as PlaceOrderRequest.OrderItemRequest.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderItemRequest {

        @NotNull(message = "Stock item ID is required")
        @Positive(message = "Stock item ID must be a positive number")
        private Long stockItemId;

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Order quantity must be at least 1")
        private Integer quantity;
    }
}
