package com.psl.oms.service;

import com.psl.oms.dto.request.CreateCustomerRequest;
import com.psl.oms.dto.response.CustomerResponse;
import com.psl.oms.entity.Customer;
import com.psl.oms.exception.DuplicateResourceException;
import com.psl.oms.exception.ResourceNotFoundException;
import com.psl.oms.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * CustomerServiceTest — pure unit tests for CustomerService.
 *
 * @ExtendWith(MockitoExtension.class) enables Mockito without Spring context.
 * Tests run in milliseconds — no DB, no Flyway, no HTTP.
 *
 * Pattern: Arrange → Act → Assert.
 * Mockito stubs repository behaviour; we verify service logic only.
 */
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerService customerService;

    private Customer sampleCustomer;

    @BeforeEach
    void setUp() {
        sampleCustomer = Customer.builder()
                .id(1L)
                .name("Alice Smith")
                .cellNumber("9876543210")
                .address("10 Test Lane")
                .build();
    }

    // ── createCustomer ───────────────────────────────────────────────────

    @Test
    @DisplayName("createCustomer — saves and returns customer when cell number is unique")
    void createCustomer_succeeds_whenCellNumberIsUnique() {
        CreateCustomerRequest request = CreateCustomerRequest.builder()
                .name("Alice Smith")
                .cellNumber("9876543210")
                .address("10 Test Lane")
                .build();

        when(customerRepository.existsByCellNumber("9876543210")).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenReturn(sampleCustomer);

        CustomerResponse response = customerService.createCustomer(request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Alice Smith");
        assertThat(response.getCellNumber()).isEqualTo("9876543210");
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    @DisplayName("createCustomer — throws DuplicateResourceException when cell number exists")
    void createCustomer_throws_whenCellNumberIsDuplicate() {
        CreateCustomerRequest request = CreateCustomerRequest.builder()
                .name("Bob Jones")
                .cellNumber("9876543210")
                .build();

        when(customerRepository.existsByCellNumber("9876543210")).thenReturn(true);

        assertThatThrownBy(() -> customerService.createCustomer(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("9876543210");

        verify(customerRepository, never()).save(any());
    }

    // ── getCustomerById ──────────────────────────────────────────────────

    @Test
    @DisplayName("getCustomerById — returns customer DTO when found")
    void getCustomerById_returnsDto_whenFound() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(sampleCustomer));

        CustomerResponse response = customerService.getCustomerById(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Alice Smith");
    }

    @Test
    @DisplayName("getCustomerById — throws ResourceNotFoundException when not found")
    void getCustomerById_throws_whenNotFound() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getCustomerById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ── getAllCustomers ──────────────────────────────────────────────────

    @Test
    @DisplayName("getAllCustomers — returns all customers as DTOs")
    void getAllCustomers_returnsAllMapped() {
        Customer second = Customer.builder().id(2L).name("Bob").cellNumber("1234567890").build();
        when(customerRepository.findAll()).thenReturn(List.of(sampleCustomer, second));

        List<CustomerResponse> result = customerService.getAllCustomers();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(CustomerResponse::getName)
                .containsExactly("Alice Smith", "Bob");
    }

    @Test
    @DisplayName("getAllCustomers — returns empty list when no customers exist")
    void getAllCustomers_returnsEmptyList_whenNoneExist() {
        when(customerRepository.findAll()).thenReturn(List.of());

        assertThat(customerService.getAllCustomers()).isEmpty();
    }
}
