package com.psl.oms.service;

import com.psl.oms.dto.request.PlaceOrderRequest;
import com.psl.oms.dto.response.BillResponse;
import com.psl.oms.dto.response.OrderItemResponse;
import com.psl.oms.dto.response.OrderResponse;
import com.psl.oms.entity.*;
import com.psl.oms.exception.BusinessRuleException;
import com.psl.oms.exception.ResourceNotFoundException;
import com.psl.oms.repository.PurchaseOrderRepository;
import com.psl.oms.repository.StockItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * OrderService — business logic for the full order lifecycle.
 *
 * Handles order placement, shipping, retrieval, delayed-order detection,
 * and invoice generation.
 *
 * ── Why placeOrder() is @Transactional ───────────────────────────────────
 * The original project had a critical bug: PurchaseOrderDAO.insert() and
 * OrderItemDAO.insert() were two separate JDBC calls with no transaction.
 * If the second call failed, the order row existed in the DB with no items —
 * an undetectable corrupt state.
 *
 * Here, @Transactional wraps the entire operation:
 *   1. Validate customer exists
 *   2. Validate all stock items exist and have sufficient quantity
 *   3. Decrement stock quantities atomically
 *   4. Persist PurchaseOrder + all OrderItems in a single save (via CascadeType.ALL)
 *
 * Any RuntimeException in any step rolls back the entire transaction.
 * The DB returns to exactly the state it was in before the call was made.
 *
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class OrderService {

    // SLA: orders must be dispatched within this many days of being placed.
    private static final int SLA_DAYS = 4;

    private final PurchaseOrderRepository orderRepository;
    private final StockItemRepository stockItemRepository;
    private final CustomerService customerService;
    private final StockItemService stockItemService;

    // ── Write operations ─────────────────────────────────────────────────

    /**
     * Places a new order atomically.
     *
     * All validation, inventory decrements, and persistence happen in a single
     * database transaction. Any failure rolls back everything.
     *
     * Business rules enforced:
     *   1. Customer must exist.
     *   2. Every stock item must exist.
     *   3. Available quantity must cover the requested quantity for each item.
     *   4. All stock is checked BEFORE any decrement — prevents partial rollbacks.
     *   5. unitPrice is snapshot-copied from the current stock price.
     *   6. shipDate = placedDate + SLA_DAYS.
     *   7. Status defaults to PENDING.
     *
     * @param request validated inbound DTO containing customerId and item list
     * @return the persisted order as a response DTO
     * @throws ResourceNotFoundException if the customer or any stock item is not found
     * @throws BusinessRuleException     if any stock item has insufficient quantity
     */
    @Transactional
    public OrderResponse placeOrder(PlaceOrderRequest request) {
        log.info("Placing order for customer ID: {}", request.getCustomerId());

        // Step 1: validate customer exists (throws 404 if not found)
        Customer customer = customerService.findCustomerOrThrow(request.getCustomerId());

        // Step 2: validate ALL stock items before doing anything to the DB.
        // This avoids a scenario where items 1–3 pass but item 4 fails,
        // leaving decrement calls half-done.
        List<OrderItemData> validatedItems = new ArrayList<>();
        for (PlaceOrderRequest.OrderItemRequest itemReq : request.getItems()) {
            StockItem stockItem = stockItemService.findStockItemOrThrow(itemReq.getStockItemId());

            if (stockItem.getQuantity() < itemReq.getQuantity()) {
                throw new BusinessRuleException(
                    String.format(
                        "Insufficient stock for '%s'. Requested: %d, Available: %d",
                        stockItem.getName(), itemReq.getQuantity(), stockItem.getQuantity()
                    )
                );
            }
            validatedItems.add(new OrderItemData(stockItem, itemReq.getQuantity()));
        }

        // Step 3: decrement stock quantities — only reached if ALL items passed validation.
        // The WHERE quantity >= :amount guard in decrementQuantity handles the race condition
        // where two concurrent requests both pass the quantity check above.
        for (OrderItemData data : validatedItems) {
            int rowsUpdated = stockItemRepository.decrementQuantity(
                data.stockItem().getId(), data.quantity()
            );
            if (rowsUpdated == 0) {
                throw new BusinessRuleException(
                    "Stock for '" + data.stockItem().getName()
                    + "' was depleted by a concurrent order. Please try again."
                );
            }
        }

        // Step 4: build and persist the order with all items in one save call.
        // CascadeType.ALL on PurchaseOrder.orderItems means the single save()
        // also inserts every OrderItem — no separate repository call needed.
        LocalDate today = LocalDate.now();
        PurchaseOrder order = PurchaseOrder.builder()
                .customer(customer)
                .placedDate(today)
                .shipDate(today.plusDays(SLA_DAYS))
                .status(OrderStatus.PENDING)
                .build();

        for (OrderItemData data : validatedItems) {
            OrderItem item = OrderItem.builder()
                    .stockItem(data.stockItem())
                    .quantity(data.quantity())
                    .unitPrice(data.stockItem().getPrice())  // snapshot price at order time
                    .build();
            order.addOrderItem(item);  // keeps both sides of the relationship in sync
        }

        PurchaseOrder saved = orderRepository.save(order);
        log.info("Order {} placed. Customer: {}, Items: {}",
                saved.getId(), customer.getName(), saved.getOrderItems().size());

        return OrderResponse.from(saved);
    }

    /**
     * Ships an order — transitions its status from PENDING to SHIPPED.
     *
     * Business rules:
     *   - Order must exist (404 if not).
     *   - Order must currently be PENDING (422 if already shipped).
     *   - actualShipDate is set to today.
     *
     * @param orderId the order to ship
     * @return the updated order as a response DTO
     * @throws ResourceNotFoundException if the order does not exist
     * @throws BusinessRuleException     if the order is already shipped
     */
    @Transactional
    public OrderResponse shipOrder(Long orderId) {
        log.info("Shipping order ID: {}", orderId);
        PurchaseOrder order = findOrderOrThrow(orderId);

        if (order.getStatus() == OrderStatus.SHIPPED) {
            throw new BusinessRuleException("Order " + orderId + " has already been shipped.");
        }

        order.setStatus(OrderStatus.SHIPPED);
        order.setActualShipDate(LocalDate.now());

        PurchaseOrder saved = orderRepository.save(order);
        log.info("Order {} shipped on {}", orderId, saved.getActualShipDate());
        return OrderResponse.from(saved);
    }

    // ── Read operations ──────────────────────────────────────────────────

    /**
     * Fetches a single order by ID with all its line items.
     *
     * Runs in a read-only transaction (class-level default) which keeps
     * LAZY associations accessible while the method executes.
     *
     * @param orderId the order's surrogate key
     * @return the order as a response DTO
     * @throws ResourceNotFoundException if the order does not exist
     */
    public OrderResponse getOrderById(Long orderId) {
        log.debug("Fetching order ID: {}", orderId);
        return OrderResponse.from(findOrderOrThrow(orderId));
    }

    /**
     * Fetches all orders, optionally filtered by customerId.
     *
     * @param customerId optional — when provided, returns only that customer's orders
     * @return list of matching orders as response DTOs
     */
    public List<OrderResponse> getAllOrders(Long customerId) {
        List<PurchaseOrder> orders;
        if (customerId != null) {
            log.debug("Fetching orders for customer ID: {}", customerId);
            orders = orderRepository.findByCustomerId(customerId);
        } else {
            log.debug("Fetching all orders");
            orders = orderRepository.findAll();
        }
        return orders.stream().map(OrderResponse::from).toList();
    }

    /**
     * Returns all orders that have missed their 4-day SLA and are still PENDING.
     *
     * Migrated from the original PurchaseOrderDAO.fetchDelayedOrders().
     * Runs on-demand via GET /api/orders/delayed.
     * A nightly scheduler will also call this in Phase 3.
     *
     * @return list of delayed orders as response DTOs
     */
    public List<OrderResponse> getDelayedOrders() {
        log.debug("Fetching delayed orders");
        return orderRepository.findDelayedOrders(OrderStatus.PENDING, LocalDate.now())
                .stream()
                .map(OrderResponse::from)
                .toList();
    }

    /**
     * Generates an invoice for an order.
     *
     * Replaces the original BillGenerator which wrote .txt files to disk.
     * Returns the same invoice data as structured JSON.
     *
     * Runs in a read-only transaction (class-level default) so LAZY associations
     * (customer, orderItems, stockItem) are accessible throughout the method.
     *
     * @param orderId the order to invoice
     * @return a complete invoice as a response DTO
     * @throws ResourceNotFoundException if the order does not exist
     */
    public BillResponse generateBill(Long orderId) {
        log.debug("Generating bill for order ID: {}", orderId);
        PurchaseOrder order = findOrderOrThrow(orderId);
        Customer customer = order.getCustomer();

        List<OrderItemResponse> itemResponses = order.getOrderItems().stream()
                .map(OrderItemResponse::from)
                .toList();

        BigDecimal grandTotal = itemResponses.stream()
                .map(OrderItemResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return BillResponse.builder()
                .orderId(order.getId())
                .placedDate(order.getPlacedDate())
                .actualShipDate(order.getActualShipDate())
                .customerId(customer.getId())
                .customerName(customer.getName())
                .customerAddress(customer.getAddress())
                .customerCellNumber(customer.getCellNumber())
                .items(itemResponses)
                .grandTotal(grandTotal)
                .build();
    }

    // ── Internal helpers ─────────────────────────────────────────────────

    /**
     * Loads a PurchaseOrder by ID or throws a descriptive 404.
     *
     * @param orderId the order's surrogate key
     * @return a managed PurchaseOrder entity
     * @throws ResourceNotFoundException if not found
     */
    private PurchaseOrder findOrderOrThrow(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
    }

    /**
     * Internal value holder used during order placement.
     *
     * Carries a validated StockItem + requested quantity pair through the
     * two-phase (validate-all-then-decrement-all) logic. Using a record is
     * cleaner than a raw Object[] or a pair of parallel lists.
     */
    private record OrderItemData(StockItem stockItem, int quantity) {}
}
