package com.psl.oms.async;

import com.psl.oms.dto.response.OrderResponse;
import com.psl.oms.entity.OrderStatus;
import com.psl.oms.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * DelayedOrderSchedulerTest — unit tests for the nightly delayed-order scanner.
 *
 * The scheduler delegates all logic to OrderService.getDelayedOrders(). These tests
 * verify the scheduler:
 *   1. Calls the service method exactly once per scan
 *   2. Handles an empty result without errors
 *   3. Handles a non-empty result (just logs — no return value to assert)
 *   4. Handles a service exception without re-throwing (schedulers must never crash)
 *
 * The @Scheduled annotation itself is not exercised here — that is Spring infrastructure.
 * We test the METHOD BEHAVIOUR, not the scheduling mechanism.
 */
@ExtendWith(MockitoExtension.class)
class DelayedOrderSchedulerTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private DelayedOrderScheduler scheduler;

    @Test
    @DisplayName("scanDelayedOrders — calls getDelayedOrders exactly once")
    void scanDelayedOrders_callsService_exactlyOnce() {
        when(orderService.getDelayedOrders()).thenReturn(List.of());

        scheduler.scanDelayedOrders();

        verify(orderService, times(1)).getDelayedOrders();
    }

    @Test
    @DisplayName("scanDelayedOrders — completes without error when no delayed orders exist")
    void scanDelayedOrders_completesCleanly_whenNoDelayedOrders() {
        when(orderService.getDelayedOrders()).thenReturn(List.of());

        // Should not throw
        scheduler.scanDelayedOrders();

        verify(orderService).getDelayedOrders();
    }

    @Test
    @DisplayName("scanDelayedOrders — completes without error when delayed orders are found")
    void scanDelayedOrders_completesCleanly_whenDelayedOrdersExist() {
        List<OrderResponse> delayed = List.of(
                buildDelayedOrderResponse(1L, "Alice"),
                buildDelayedOrderResponse(2L, "Bob")
        );
        when(orderService.getDelayedOrders()).thenReturn(delayed);

        // Should not throw — just logs
        scheduler.scanDelayedOrders();

        verify(orderService).getDelayedOrders();
    }

    @Test
    @DisplayName("scanDelayedOrders — does NOT re-throw when service throws an exception")
    void scanDelayedOrders_doesNotRethrow_whenServiceThrows() {
        // If the scheduler method throws, Spring stops scheduling future runs.
        // The scheduler MUST catch and log all exceptions — never let them propagate.
        when(orderService.getDelayedOrders())
                .thenThrow(new RuntimeException("DB connection lost"));

        // assertDoesNotThrow equivalent: if an exception is thrown the test fails
        scheduler.scanDelayedOrders();

        verify(orderService).getDelayedOrders();
    }

    private OrderResponse buildDelayedOrderResponse(Long id, String customerName) {
        return OrderResponse.builder()
                .id(id)
                .customerId(1L)
                .customerName(customerName)
                .placedDate(LocalDate.now().minusDays(6))
                .shipDate(LocalDate.now().minusDays(2))   // missed SLA
                .status(OrderStatus.PENDING)
                .items(List.of())
                .totalAmount(BigDecimal.ZERO)
                .delayed(true)
                .build();
    }
}
