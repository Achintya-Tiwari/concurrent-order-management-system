package com.psl.oms.controller;

import com.psl.oms.async.BillGeneratorService;
import com.psl.oms.async.ConcurrentOrderProcessor;
import com.psl.oms.dto.request.PlaceOrderRequest;
import com.psl.oms.dto.response.AsyncBillResponse;
import com.psl.oms.dto.response.BillResponse;
import com.psl.oms.dto.response.OrderResponse;
import com.psl.oms.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * OrderController — REST endpoints for the full order lifecycle.
 *
 * Phase 2 endpoints (unchanged):
 *   POST   /api/orders                    — place a new order (transactional)
 *   GET    /api/orders/{id}               — get a specific order
 *   GET    /api/orders                    — list all orders (optional ?customerId= filter)
 *   PATCH  /api/orders/{id}/ship          — ship an order
 *   GET    /api/orders/delayed            — list all delayed orders
 *   GET    /api/orders/{id}/bill          — generate an invoice (synchronous)
 *
 * Phase 3 additions:
 *   GET    /api/orders/{id}/bill/async    — trigger async bill generation (non-blocking)
 *   POST   /api/orders/batch/fetch        — fetch multiple orders concurrently
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Orders", description = "Order placement, shipping, and lifecycle management")
public class OrderController {

    private final OrderService orderService;
    private final BillGeneratorService billGeneratorService;        // Phase 3
    private final ConcurrentOrderProcessor concurrentOrderProcessor; // Phase 3

    // ── Phase 2 endpoints (unchanged) ───────────────────────────────────

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Place a new order")
    public OrderResponse placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        return orderService.placeOrder(request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an order by ID")
    public OrderResponse getOrder(@PathVariable Long id) {
        return orderService.getOrderById(id);
    }

    @GetMapping
    @Operation(summary = "List all orders, optionally filtered by customer")
    public List<OrderResponse> getAllOrders(
            @Parameter(description = "Filter orders by customer ID")
            @RequestParam(required = false) Long customerId) {
        return orderService.getAllOrders(customerId);
    }

    @PatchMapping("/{id}/ship")
    @Operation(summary = "Ship an order (PENDING → SHIPPED)")
    public OrderResponse shipOrder(@PathVariable Long id) {
        return orderService.shipOrder(id);
    }

    @GetMapping("/delayed")
    @Operation(summary = "List all delayed orders (PENDING and past SLA ship date)")
    public List<OrderResponse> getDelayedOrders() {
        return orderService.getDelayedOrders();
    }

    @GetMapping("/{id}/bill")
    @Operation(summary = "Generate an invoice for an order (synchronous)")
    public BillResponse getBill(@PathVariable Long id) {
        return orderService.generateBill(id);
    }

    // ── Phase 3 endpoints ────────────────────────────────────────────────

    /**
     * GET /api/orders/{id}/bill/async
     *
     * Non-blocking bill generation. The HTTP response returns immediately with HTTP 202
     * (Accepted) while bill assembly runs on a background thread from the omsTaskExecutor pool.
     *
     * ── What the client experiences ──────────────────────────────────────────
     *   1. Client hits GET /api/orders/5/bill/async
     *   2. Server responds instantly with:
     *        { "orderId": 5, "status": "PROCESSING", "requestedAt": "..." }
     *   3. Server logs the completed bill on a background thread seconds later
     *
     * ── Why CompletableFuture as the return type? ─────────────────────────────
     * When Spring MVC sees a CompletableFuture return type, it releases the Tomcat
     * HTTP thread immediately and resumes writing the response when the future completes.
     * The controller thread is freed before bill generation finishes — true non-blocking.
     *
     * Contrast with the synchronous /bill endpoint where the Tomcat thread is held
     * for the entire duration of generateBill().
     *
     * ── HTTP 202 Accepted ────────────────────────────────────────────────────
     * 202 is the correct status for "request accepted, processing in background".
     * 200 would imply the work is done. 201 (Created) is for resource creation.
     *
     * ── Path conflict note ───────────────────────────────────────────────────
     * Spring resolves /{id}/bill/async correctly because it is a longer, more
     * specific path than /{id}/bill. No ambiguity.
     */
    @GetMapping("/{id}/bill/async")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Trigger async bill generation (non-blocking, returns immediately)")
    public CompletableFuture<AsyncBillResponse> getBillAsync(@PathVariable Long id) {
        log.info("[CONTROLLER] Async bill generation requested for order #{}", id);

        // Fire the async task — returns immediately (the future is already running on a pool thread)
        CompletableFuture<BillResponse> billFuture = billGeneratorService.generateBillAsync(id);

        // Register a completion callback for logging only — controller does NOT block on the future
        billFuture.thenAccept(bill ->
            log.info("[CONTROLLER] Async bill for order #{} is ready — total={}", id, bill.getGrandTotal())
        ).exceptionally(ex -> {
            log.error("[CONTROLLER] Async bill for order #{} failed — {}", id, ex.getMessage());
            return null;
        });

        // Return the "accepted" acknowledgement immediately, on the HTTP thread.
        // The client gets this response before the bill is generated.
        return CompletableFuture.completedFuture(AsyncBillResponse.accepted(id));
    }

    /**
     * POST /api/orders/batch/fetch
     *
     * Fetches multiple orders concurrently using a fixed thread pool.
     * Demonstrates the ConcurrentOrderProcessor with a real HTTP endpoint.
     *
     * Request body: a list of order IDs, e.g. [1, 2, 3, 4, 5]
     * Response: a list of OrderResponse DTOs (same shape as single-order GET).
     *
     * ── When would this be useful in production? ──────────────────────────────
     * When a client needs N orders and calling GET /api/orders/{id} N times
     * sequentially is too slow. This endpoint parallelises those N lookups.
     *
     * ── Request size guard ────────────────────────────────────────────────────
     * Capped at 20 IDs per request. Without a cap, a malicious or buggy client
     * could submit thousands of IDs and exhaust the thread pool queue.
     */
    @PostMapping("/batch/fetch")
    @Operation(summary = "Fetch multiple orders concurrently (batch lookup)")
    public List<OrderResponse> fetchOrdersBatch(@RequestBody List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        if (orderIds.size() > 20) {
            throw new com.psl.oms.exception.BusinessRuleException(
                "Batch fetch supports a maximum of 20 order IDs per request. Received: " + orderIds.size()
            );
        }
        log.info("[CONTROLLER] Batch fetch requested for {} order IDs", orderIds.size());
        return concurrentOrderProcessor.fetchOrdersConcurrently(orderIds);
    }
}
