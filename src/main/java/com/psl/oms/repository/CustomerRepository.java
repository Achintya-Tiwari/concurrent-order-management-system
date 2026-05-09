package com.psl.oms.repository;

import com.psl.oms.entity.Customer;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * CustomerRepository – data-access interface for {@link Customer} entities.
 *
 * Extends JpaRepository, which provides standard CRUD out of the box.
 * Spring Data generates a proxy implementation at startup.
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /**
     * Checks whether a customer with the given cell number already exists.
     *
     * Used to enforce the uniqueness rule before an insert so the caller
     * receives a clean 409 instead of a raw DB constraint violation.
     *
     * @param cellNumber the 10–15 digit mobile number to check
     * @return true if a customer with this number is already registered
     */
    boolean existsByCellNumber(String cellNumber);

    /**
     * Finds a customer by their cell number.
     *
     * @param cellNumber the cell number to search
     * @return an Optional containing the customer if found, empty otherwise
     */
    Optional<Customer> findByCellNumber(String cellNumber);

    /**
     * Finds the customer who has placed the most orders.
     *
     *
     * Returns a List (not Optional) because the Pageable overload requires a
     * collection return type. ReportingService converts the list to an Optional.
     *
     * @param pageable must be PageRequest.of(0, 1) — caller's responsibility
     * @return a single-element list with the top customer, or empty list if none
     */
    @Query("""
        SELECT c FROM Customer c
        JOIN c.orders o
        GROUP BY c
        ORDER BY COUNT(o) DESC
        """)
    List<Customer> findTopCustomerByOrderCount(Pageable pageable);
}
