package com.psl.oms.controller;

import com.psl.oms.dto.request.CreateCustomerRequest;
import com.psl.oms.dto.response.CustomerResponse;
import com.psl.oms.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CustomerController — REST endpoints for customer management.
 *
 * Controller responsibilities (and only these):
 *   1. Map HTTP verbs + paths to service method calls.
 *   2. Declare @Valid on request bodies to trigger Bean Validation.
 *   3. Return the correct HTTP status code (201 for creation, 200 for reads).
 *   4. Annotate with Swagger @Operation for API documentation.
 *
 * No business logic lives here. Every decision lives in CustomerService.
 */
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Customers", description = "Customer registration and lookup")
public class CustomerController {

    private final CustomerService customerService;

    /**
     * POST /api/customers
     * Registers a new customer.
     *
     * Returns 201 Created with the new customer in the body.
     * Returns 409 Conflict if the cell number already exists.
     * Returns 400 Bad Request if validation fails (@Valid).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new customer")
    public CustomerResponse createCustomer(@Valid @RequestBody CreateCustomerRequest request) {
        return customerService.createCustomer(request);
    }

    /**
     * GET /api/customers/{id}
     * Retrieves a customer by their ID.
     *
     * Returns 200 OK with the customer.
     * Returns 404 Not Found if the ID does not exist.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get a customer by ID")
    public CustomerResponse getCustomer(@PathVariable Long id) {
        return customerService.getCustomerById(id);
    }

    /**
     * GET /api/customers
     * Retrieves all customers.
     *
     * Returns 200 OK with a (possibly empty) list.
     */
    @GetMapping
    @Operation(summary = "List all customers")
    public List<CustomerResponse> getAllCustomers() {
        return customerService.getAllCustomers();
    }
}
