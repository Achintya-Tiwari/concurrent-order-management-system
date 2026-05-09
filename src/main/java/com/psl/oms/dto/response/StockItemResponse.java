package com.psl.oms.dto.response;

import com.psl.oms.entity.StockItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** StockItemResponse — outbound DTO for /api/stocks endpoints. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockItemResponse {

    private Long id;
    private String name;
    private BigDecimal price;
    private Integer quantity;

    public static StockItemResponse from(StockItem item) {
        return StockItemResponse.builder()
                .id(item.getId())
                .name(item.getName())
                .price(item.getPrice())
                .quantity(item.getQuantity())
                .build();
    }
}
