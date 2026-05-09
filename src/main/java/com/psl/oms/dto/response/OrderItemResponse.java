package com.psl.oms.dto.response;

import com.psl.oms.entity.OrderItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * OrderItemResponse — one line item within an OrderResponse or BillResponse.
 *
 * stockItemName is denormalised here so the client doesn't need a separate
 * lookup to resolve the stock item ID to a readable name.
 * This field is populated from the LAZY StockItem association, which must
 * be loaded within the transaction before mapping occurs (OrderService handles this).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemResponse {

    private Long id;
    private Long stockItemId;
    private String stockItemName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;

    public static OrderItemResponse from(OrderItem item) {
        return OrderItemResponse.builder()
                .id(item.getId())
                .stockItemId(item.getStockItem().getId())
                .stockItemName(item.getStockItem().getName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .subtotal(item.getSubtotal())
                .build();
    }
}
