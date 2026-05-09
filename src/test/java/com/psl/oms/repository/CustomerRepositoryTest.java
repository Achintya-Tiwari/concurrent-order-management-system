package com.psl.oms.repository;

import com.psl.oms.entity.Customer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * CustomerRepositoryTest – slice test for {@link CustomerRepository}.
 *
 * <p>{@code @DataJpaTest} starts only the JPA slice of the Spring context:
 * <ul>
 *   <li>Configures an in-memory H2 database</li>
 *   <li>Runs Flyway (or Hibernate ddl-auto) to create the schema</li>
 *   <li>Injects {@code @Repository} and {@code @Entity} beans</li>
 *   <li>Does NOT start the web layer (no controllers, no services)</li>
 * </ul>
 *
 * <p>Each test runs in a transaction that is rolled back after the test —
 * so tests are fully isolated without needing to clean up the DB manually.
 *
 * <p>Note: @DataJpaTest uses an embedded H2 DB by default.
 * Flyway V1 DDL must be H2-compatible.  MySQL-specific syntax that doesn't
 * work in H2 is handled by the test profile (application-test.yml in Phase 2).
 */
@DataJpaTest
@ActiveProfiles("test")
class CustomerRepositoryTest {

    @Autowired
    private CustomerRepository customerRepository;

    private Customer savedCustomer;

    @BeforeEach
    void setUp() {
        savedCustomer = customerRepository.save(
            Customer.builder()
                .name("Alice Smith")
                .cellNumber("9876543210")
                .address("10 Test Lane")
                .build()
        );
    }

    @Test
    @DisplayName("existsByCellNumber returns true for existing cell number")
    void existsByCellNumber_returnsTrue_whenExists() {
        assertThat(customerRepository.existsByCellNumber("9876543210")).isTrue();
    }

    @Test
    @DisplayName("existsByCellNumber returns false for unknown cell number")
    void existsByCellNumber_returnsFalse_whenNotExists() {
        assertThat(customerRepository.existsByCellNumber("0000000000")).isFalse();
    }

    @Test
    @DisplayName("findByCellNumber returns customer when found")
    void findByCellNumber_returnsCustomer_whenFound() {
        Optional<Customer> result = customerRepository.findByCellNumber("9876543210");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Alice Smith");
    }

    @Test
    @DisplayName("findByCellNumber returns empty when not found")
    void findByCellNumber_returnsEmpty_whenNotFound() {
        Optional<Customer> result = customerRepository.findByCellNumber("1111111111");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("save persists a customer and assigns an auto-generated ID")
    void save_persistsCustomer_withGeneratedId() {
        Customer newCustomer = Customer.builder()
            .name("Bob Jones")
            .cellNumber("1234567890")
            .build();

        Customer saved = customerRepository.save(newCustomer);

        assertThat(saved.getId()).isNotNull().isPositive();
        assertThat(customerRepository.findById(saved.getId())).isPresent();
    }

    @Test
    @DisplayName("findById returns empty for non-existent ID")
    void findById_returnsEmpty_forNonExistentId() {
        assertThat(customerRepository.findById(9999L)).isEmpty();
    }
}
