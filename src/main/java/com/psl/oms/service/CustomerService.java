package com.psl.oms.service;

import com.psl.oms.dto.request.CreateCustomerRequest;
import com.psl.oms.dto.response.CustomerResponse;
import com.psl.oms.entity.Customer;
import com.psl.oms.exception.DuplicateResourceException;
import com.psl.oms.exception.ResourceNotFoundException;
import com.psl.oms.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CustomerService — business logic for customer management.
 *
 * Constructor injection via @RequiredArgsConstructor (Lombok generates the
 * constructor from all final fields). This is preferred over @Autowired on
 * fields because:
 *   - Dependencies are explicit and immutable.
 *   - The class is testable without a Spring context (just call the constructor).
 *   - Circular dependency issues surface at compile time, not runtime.
 *
 * Transaction strategy:
 *   - @Transactional(readOnly = true) on reads — Hibernate skips dirty checking,
 *     the connection pool can route to read replicas, and performance improves.
 *   - @Transactional (read-write) on writes — ensures atomicity and rollback on failure.
 *   - The class-level annotation sets the default; method-level annotations override it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;

    // ── Write operations ─────────────────────────────────────────────────

    /**
     * Creates a new customer after validating the cell number is not already taken.
     *
     * Original rule: "Duplicate entries for the Customer table are skipped if a
     * consumer has an existing cell number." We now surface this as HTTP 409
     * instead of silently discarding the request, which is far more debuggable.
     *
     * @param request validated inbound DTO from the controller
     * @return the saved customer as an API-safe response DTO
     * @throws DuplicateResourceException if the cell number already exists
     */
    @Transactional
    public CustomerResponse createCustomer(CreateCustomerRequest request) {
        log.info("Creating customer with cell number: {}", request.getCellNumber());

        if (customerRepository.existsByCellNumber(request.getCellNumber())) {
            throw new DuplicateResourceException("Customer", "cellNumber", request.getCellNumber());
        }

        Customer customer = Customer.builder()
                .name(request.getName())
                .address(request.getAddress())
                .cellNumber(request.getCellNumber())
                .build();

        Customer saved = customerRepository.save(customer);
        log.info("Customer created with ID: {}", saved.getId());
        return CustomerResponse.from(saved);
    }

    // ── Read operations ──────────────────────────────────────────────────

    /**
     * Fetches a single customer by ID.
     *
     * @param id the customer's surrogate key
     * @return the customer as a response DTO
     * @throws ResourceNotFoundException if no customer with this ID exists
     */
    public CustomerResponse getCustomerById(Long id) {
        log.debug("Fetching customer with ID: {}", id);
        Customer customer = findCustomerOrThrow(id);
        return CustomerResponse.from(customer);
    }

    /**
     * Fetches all customers in the system.
     *
     * For production use with large datasets this would need pagination
     * (Pageable parameter + Page<CustomerResponse> return type). Added
     * as a Phase 3 improvement note.
     *
     * @return list of all customers as response DTOs
     */
    public List<CustomerResponse> getAllCustomers() {
        log.debug("Fetching all customers");
        return customerRepository.findAll()
                .stream()
                .map(CustomerResponse::from)
                .toList();
    }

    // ── Internal helpers (package-private for testability) ───────────────

    /**
     * Loads a Customer entity by ID or throws a descriptive 404.
     * Reused by OrderService when it needs to attach a customer to an order.
     *
     * @param id the customer's ID
     * @return a managed Customer entity
     * @throws ResourceNotFoundException if not found
     */
    Customer findCustomerOrThrow(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", id));
    }
}
