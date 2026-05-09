package com.psl.oms.repository;

import com.psl.oms.entity.StockItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * StockItemRepository – data-access interface for {@link StockItem} entities.
 *
 * <p>Replaces the original {@code StockItemDAO} class, preserving
 * its query intentions while leveraging Spring Data's code-generation.
 */
@Repository
public interface StockItemRepository extends JpaRepository<StockItem, Long> {

    /**
     * Checks whether a stock item with the given name already exists.
     *
     * <p>Enforces the original rule: "Duplicate stock items (by name)
     * are skipped if the name already exists in the table."
     *
     * @param name the product name to check (case-sensitive)
     * @return {@code true} if a stock item with this name exists
     */
    boolean existsByName(String name);

    /**
     * Looks up a stock item by its exact name.
     *
     * @param name the product name to search
     * @return Optional containing the stock item if found, empty otherwise
     */
    Optional<StockItem> findByName(String name);

    /**
     * Decrements the available quantity of a stock item.
     *
     * <p>Used during order placement to track inventory consumption.
     * The {@code @Modifying} annotation is required for UPDATE/DELETE JPQL queries.
     * {@code clearAutomatically = true} clears the persistence context after
     * the update so subsequent finds return the fresh value.
     *
     * @param stockItemId the ID of the stock item to adjust
     * @param amount      the quantity to subtract (must be positive)
     */
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE StockItem s
        SET s.quantity = s.quantity - :amount
        WHERE s.id = :stockItemId AND s.quantity >= :amount
        """)
    int decrementQuantity(@Param("stockItemId") Long stockItemId,
                          @Param("amount") int amount);
}
