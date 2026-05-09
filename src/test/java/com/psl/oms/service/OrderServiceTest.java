package com.psl.oms.service;

import com.psl.oms.dto.request.PlaceOrderRequest;
import com.psl.oms.dto.response.OrderResponse;
import com.psl.oms.entity.*;
import com.psl.oms.exception.BusinessRuleException;
import com.psl.oms.exception.ResourceNotFoundException;
import com.psl.oms.repository.PurchaseOrderRepository;
import com.psl.oms.repository.StockItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * OrderServiceTest — unit tests for the transactional order service.
 *
 * All collaborators (repositories, CustomerService, StockItemService) are mocked.
 * No Spring context, no DB — tests run in milliseconds.
 *
 * Pattern: Arrange → Act → Assert.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    // OrderItems are persisted via CascadeType.ALL on PurchaseOrder.orderItems.
    @Mock private PurchaseOrderRepository orderRepository;
    @Mock private StockItemRepository stockItemRepository;
    @Mock private CustomerService customerService;
    @Mock private StockItemService stockItemService;

    @InjectMocks
    private OrderService orderService;

    private Customer customer;
    private StockItem stockItem;

    @BeforeEach
    void setUp() {
        customer = Customer.builder()
                .id(1L).name("Alice").cellNumber("9876543210").build();

        stockItem = StockItem.builder()
                .id(10L).name("Widget").price(new BigDecimal("25.00")).quantity(50).build();
    }

    // ── placeOrder ───────────────────────────────────────────────────────

    @Test
    @DisplayName("placeOrder — creates order with PENDING status and correct SLA ship date")
    void placeOrder_createsOrder_withCorrectDefaults() {
        PlaceOrderRequest request = PlaceOrderRequest.builder()
                .customerId(1L)
                .items(List.of(new PlaceOrderRequest.OrderItemRequest(10L, 3)))
                .build();

        when(customerService.findCustomerOrThrow(1L)).thenReturn(customer);
        when(stockItemService.findStockItemOrThrow(10L)).thenReturn(stockItem);
        when(stockItemRepository.decrementQuantity(10L, 3)).thenReturn(1);

        PurchaseOrder savedOrder = buildSavedOrder(customer, stockItem, 3);
        when(orderRepository.save(any(PurchaseOrder.class))).thenReturn(savedOrder);

        OrderResponse response = orderService.placeOrder(request);

        assertThat(response.getCustomerId()).isEqualTo(1L);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.getShipDate()).isEqualTo(LocalDate.now().plusDays(4));
        assertThat(response.getItems()).hasSize(1);
        verify(stockItemRepository).decrementQuantity(10L, 3);
        verify(orderRepository).save(any(PurchaseOrder.class));
    }

    @Test
    @DisplayName("placeOrder — throws BusinessRuleException when stock is insufficient")
    void placeOrder_throws_whenStockInsufficient() {
        StockItem lowStock = StockItem.builder()
                .id(10L).name("Widget").price(new BigDecimal("25.00")).quantity(1).build();

        PlaceOrderRequest request = PlaceOrderRequest.builder()
                .customerId(1L)
                .items(List.of(new PlaceOrderRequest.OrderItemRequest(10L, 5)))
                .build();

        when(customerService.findCustomerOrThrow(1L)).thenReturn(customer);
        when(stockItemService.findStockItemOrThrow(10L)).thenReturn(lowStock);

        assertThatThrownBy(() -> orderService.placeOrder(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Insufficient stock")
                .hasMessageContaining("Widget");

        // Nothing should be decremented or saved when validation fails
        verify(stockItemRepository, never()).decrementQuantity(any(), any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("placeOrder — throws ResourceNotFoundException when customer not found")
    void placeOrder_throws_whenCustomerNotFound() {
        when(customerService.findCustomerOrThrow(99L))
                .thenThrow(new ResourceNotFoundException("Customer", "id", 99L));

        PlaceOrderRequest request = PlaceOrderRequest.builder()
                .customerId(99L)
                .items(List.of(new PlaceOrderRequest.OrderItemRequest(10L, 1)))
                .build();

        assertThatThrownBy(() -> orderService.placeOrder(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ── shipOrder ────────────────────────────────────────────────────────

    @Test
    @DisplayName("shipOrder — transitions PENDING order to SHIPPED and sets actualShipDate")
    void shipOrder_transitions_pendingToShipped() {
        PurchaseOrder order = buildSavedOrder(customer, stockItem, 1);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        // Return the same object that was saved so the response reflects the update
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.shipOrder(1L);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(response.getActualShipDate()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("shipOrder — throws BusinessRuleException when order is already SHIPPED")
    void shipOrder_throws_whenAlreadyShipped() {
        PurchaseOrder order = buildSavedOrder(customer, stockItem, 1);
        order.setStatus(OrderStatus.SHIPPED);
        order.setActualShipDate(LocalDate.now().minusDays(1));

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.shipOrder(1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already been shipped");
    }

    // ── getDelayedOrders ─────────────────────────────────────────────────

    @Test
    @DisplayName("getDelayedOrders — returns PENDING orders whose ship date has passed")
    void getDelayedOrders_returnsDelayedPendingOrders() {
        PurchaseOrder delayed = buildSavedOrder(customer, stockItem, 1);
        // Force delayed: PENDING status + ship date in the past
        delayed.setShipDate(LocalDate.now().minusDays(2));

        when(orderRepository.findDelayedOrders(OrderStatus.PENDING, LocalDate.now()))
                .thenReturn(List.of(delayed));

        List<OrderResponse> result = orderService.getDelayedOrders();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isDelayed()).isTrue();
    }

    // ── Test helper ──────────────────────────────────────────────────────

    private PurchaseOrder buildSavedOrder(Customer c, StockItem si, int qty) {
        OrderItem item = OrderItem.builder()
                .id(100L)
                .stockItem(si)
                .quantity(qty)
                .unitPrice(si.getPrice())
                .build();

        PurchaseOrder order = PurchaseOrder.builder()
                .id(1L)
                .customer(c)
                .placedDate(LocalDate.now())
                .shipDate(LocalDate.now().plusDays(4))
                .status(OrderStatus.PENDING)
                .build();

        order.addOrderItem(item);
        return order;
    }
}
