package com.psl.oms.dto.response;

import com.psl.oms.entity.Customer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CustomerResponse — outbound DTO for GET /api/customers and POST /api/customers.
 *
 * The static factory from(Customer) centralises entity-to-DTO mapping so no
 * controller or service needs inline field mapping.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerResponse {

    private Long id;
    private String name;
    private String address;
    private String cellNumber;

    public static CustomerResponse from(Customer customer) {
        return CustomerResponse.builder()
                .id(customer.getId())
                .name(customer.getName())
                .address(customer.getAddress())
                .cellNumber(customer.getCellNumber())
                .build();
    }
}
