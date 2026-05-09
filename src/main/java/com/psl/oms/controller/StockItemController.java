package com.psl.oms.controller;

import com.psl.oms.dto.request.CreateStockItemRequest;
import com.psl.oms.dto.response.StockItemResponse;
import com.psl.oms.service.StockItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * StockItemController — REST endpoints for product catalogue management.
 */
@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Stock Items", description = "Product catalogue management and inventory")
public class StockItemController {

    private final StockItemService stockItemService;

    /**
     * POST /api/stocks
     * Adds a new product to the catalogue.
     *
     * Returns HTTP 201 Created with the new stock item.
     * Returns HTTP 409 Conflict if the product name already exists.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a new stock item to the catalogue")
    public StockItemResponse createStockItem(@Valid @RequestBody CreateStockItemRequest request) {
        return stockItemService.createStockItem(request);
    }

    /**
     * GET /api/stocks/{id}
     * Retrieves a single stock item by ID.
     *
     * Returns HTTP 200 OK.
     * Returns HTTP 404 Not Found if the ID does not exist.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get a stock item by ID")
    public StockItemResponse getStockItem(@PathVariable Long id) {
        return stockItemService.getStockItemById(id);
    }

    /**
     * GET /api/stocks
     * Retrieves all stock items in the catalogue.
     *
     * Returns HTTP 200 OK with a (possibly empty) list.
     */
    @GetMapping
    @Operation(summary = "List all stock items")
    public List<StockItemResponse> getAllStockItems() {
        return stockItemService.getAllStockItems();
    }
}
