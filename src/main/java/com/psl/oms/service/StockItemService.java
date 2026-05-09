package com.psl.oms.service;

import com.psl.oms.dto.request.CreateStockItemRequest;
import com.psl.oms.dto.response.StockItemResponse;
import com.psl.oms.entity.StockItem;
import com.psl.oms.exception.DuplicateResourceException;
import com.psl.oms.exception.ResourceNotFoundException;
import com.psl.oms.repository.StockItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * StockItemService — business logic for stock/inventory management.
 *
 * Replaces the original StockItemDAO + StockItemService pair, consolidating
 * data access and business rules in one well-defined layer.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class StockItemService {

    private final StockItemRepository stockItemRepository;

    // ── Write operations ─────────────────────────────────────────────────

    /**
     * Adds a new product to the catalogue.
     *
     * Original rule: "Duplicate stock items (by name) are skipped."
     * We now return HTTP 409 instead of silently skipping.
     *
     * @param request validated inbound DTO
     * @return the saved stock item as a response DTO
     * @throws DuplicateResourceException if the product name already exists
     */
    @Transactional
    public StockItemResponse createStockItem(CreateStockItemRequest request) {
        log.info("Creating stock item: {}", request.getName());

        if (stockItemRepository.existsByName(request.getName())) {
            throw new DuplicateResourceException("StockItem", "name", request.getName());
        }

        Integer quantity = request.getQuantity() != null ? request.getQuantity() : 0;

        StockItem item = StockItem.builder()
                .name(request.getName())
                .price(request.getPrice())
                .quantity(quantity)
                .build();

        StockItem saved = stockItemRepository.save(item);
        log.info("Stock item created with ID: {}", saved.getId());
        return StockItemResponse.from(saved);
    }

    // ── Read operations ──────────────────────────────────────────────────

    /**
     * Fetches a single stock item by ID.
     *
     * @param id the stock item's surrogate key
     * @return the stock item as a response DTO
     * @throws ResourceNotFoundException if no item with this ID exists
     */
    public StockItemResponse getStockItemById(Long id) {
        log.debug("Fetching stock item with ID: {}", id);
        return StockItemResponse.from(findStockItemOrThrow(id));
    }

    /**
     * Fetches all stock items in the catalogue.
     *
     * @return list of all stock items as response DTOs
     */
    public List<StockItemResponse> getAllStockItems() {
        log.debug("Fetching all stock items");
        return stockItemRepository.findAll()
                .stream()
                .map(StockItemResponse::from)
                .toList();
    }

    // ── Internal helpers ─────────────────────────────────────────────────

    /**
     * Loads a StockItem entity by ID or throws a descriptive 404.
     * Called by OrderService during order placement to validate each line item.
     *
     * @param id the stock item's ID
     * @return a managed StockItem entity
     * @throws ResourceNotFoundException if not found
     */
    StockItem findStockItemOrThrow(Long id) {
        return stockItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StockItem", "id", id));
    }
}
