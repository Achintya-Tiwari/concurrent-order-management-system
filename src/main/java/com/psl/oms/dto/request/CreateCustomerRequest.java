package com.psl.oms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CreateCustomerRequest — inbound payload for POST /api/customers.
 *
 * Validation annotations are evaluated by @Valid in the controller before
 * the service layer is ever invoked — the service receives only clean data.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCustomerRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be 100 characters or fewer")
    private String name;

    @Size(max = 255, message = "Address must be 255 characters or fewer")
    private String address;

    /**
     * 10–15 digit mobile number. Must be unique (enforced in CustomerService
     * so the caller gets a readable 409 instead of a raw DB constraint error).
     */
    @NotBlank(message = "Cell number is required")
    @Pattern(
        regexp = "^[0-9]{10,15}$",
        message = "Cell number must contain 10 to 15 digits only"
    )
    private String cellNumber;
}
