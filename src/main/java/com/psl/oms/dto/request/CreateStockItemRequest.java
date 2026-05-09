package com.psl.oms.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * CreateStockItemRequest — inbound payload for POST /api/stocks.
 *
 * quantity is optional — if omitted in the JSON body Jackson sets it to null,
 * which the service layer converts to 0. The @Builder.Default below ensures
 * the Lombok builder also initialises the field to 0 when quantity() is not called.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateStockItemRequest {

    @NotBlank(message = "Stock item name is required")
    @Size(max = 100, message = "Name must be 100 characters or fewer")
    private String name;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than zero")
    @Digits(integer = 8, fraction = 2, message = "Price must have at most 8 integer and 2 decimal digits")
    private BigDecimal price;

    /**
     * Initial stock quantity on hand.
     *
     */
    @Min(value = 0, message = "Quantity cannot be negative")
    @Builder.Default
    private Integer quantity = 0;
}
