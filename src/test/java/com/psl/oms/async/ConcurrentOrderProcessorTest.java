package com.psl.oms.async;

import com.psl.oms.dto.response.OrderItemResponse;
import com.psl.oms.dto.response.OrderResponse;
import com.psl.oms.entity.OrderStatus;
import com.psl.oms.exception.BusinessRuleException;
import com.psl.oms.exception.ResourceNotFoundException;
import com.psl.oms.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ConcurrentOrderProcessorTest — unit tests for the concurrent batch processor.
 *
 * The ExecutorService is created directly in @BeforeEach (not from Spring) so
 * tests run without an application context. This validates the concurrency logic
 * independently from Spring wiring.
 *
 * After each test, @AfterEach shuts down the executor to prevent thread leaks
 * between tests — critical when using real thread pools in unit tests.
 */
@ExtendWith(MockitoExtension.class)
class ConcurrentOrderProcessorTest {

    @Mock
    private OrderService orderService;

    private ExecutorService testExecutor;
    private ConcurrentOrderProcessor processor;

    @BeforeEach
    void setUp() {
        // Use a small fixed pool for tests — enough threads to run the batch concurrently
        testExecutor = Executors.newFixedThreadPool(3);
        processor = new ConcurrentOrderProcessor(orderService, testExecutor);
    }

    @AfterEach
    void tearDown() {
        // Always shut down the executor after each test to avoid thread leaks
        testExecutor.shutdownNow();
    }

    // ── fetchOrdersConcurrently ──────────────────────────────────────────

    @Test
    @DisplayName("fetchOrdersConcurrently — returns all orders when all IDs are valid")
    void fetchOrdersConcurrently_returnsAllOrders_whenAllValid() {
        when(orderService.getOrderById(1L)).thenReturn(buildOrder(1L));
        when(orderService.getOrderById(2L)).thenReturn(buildOrder(2L));
        when(orderService.getOrderById(3L)).thenReturn(buildOrder(3L));

        List<OrderResponse> results = processor.fetchOrdersConcurrently(List.of(1L, 2L, 3L));

        assertThat(results).hasSize(3);
        verify(orderService, times(1)).getOrderById(1L);
        verify(orderService, times(1)).getOrderById(2L);
        verify(orderService, times(1)).getOrderById(3L);
    }

    @Test
    @DisplayName("fetchOrdersConcurrently — returns only successful results when some IDs fail")
    void fetchOrdersConcurrently_returnsPartialResults_whenSomeFail() {
        when(orderService.getOrderById(1L)).thenReturn(buildOrder(1L));
        when(orderService.getOrderById(2L))
                .thenThrow(new ResourceNotFoundException("Order", "id", 2L));
        when(orderService.getOrderById(3L)).thenReturn(buildOrder(3L));

        // Failed fetches are excluded; successful ones still returned
        List<OrderResponse> results = processor.fetchOrdersConcurrently(List.of(1L, 2L, 3L));

        assertThat(results).hasSize(2);
        assertThat(results).extracting(OrderResponse::getId)
                .containsExactlyInAnyOrder(1L, 3L);
    }

    @Test
    @DisplayName("fetchOrdersConcurrently — returns empty list when input is empty")
    void fetchOrdersConcurrently_returnsEmpty_whenInputEmpty() {
        List<OrderResponse> results = processor.fetchOrdersConcurrently(List.of());

        assertThat(results).isEmpty();
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("fetchOrdersConcurrently — returns empty list when input is null")
    void fetchOrdersConcurrently_returnsEmpty_whenInputNull() {
        List<OrderResponse> results = processor.fetchOrdersConcurrently(null);

        assertThat(results).isEmpty();
        verifyNoInteractions(orderService);
    }

    // ── shipOrdersConcurrently ───────────────────────────────────────────

    @Test
    @DisplayName("shipOrdersConcurrently — all succeed when all orders are valid and PENDING")
    void shipOrdersConcurrently_allSucceed_whenAllValid() {
        // shipOrder has no return value to stub — just needs to not throw
        doReturn(buildOrder(1L)).when(orderService).shipOrder(1L);
        doReturn(buildOrder(2L)).when(orderService).shipOrder(2L);

        ConcurrentOrderProcessor.BatchProcessingReport report =
                processor.shipOrdersConcurrently(List.of(1L, 2L));

        assertThat(report.getTotalSubmitted()).isEqualTo(2);
        assertThat(report.getSuccessful()).isEqualTo(2);
        assertThat(report.getFailed()).isEqualTo(0);
    }

    @Test
    @DisplayName("shipOrdersConcurrently — counts failures when some orders already shipped")
    void shipOrdersConcurrently_countsFailed_whenSomeAlreadyShipped() {
        doReturn(buildOrder(1L)).when(orderService).shipOrder(1L);
        doThrow(new BusinessRuleException("Order 2 has already been shipped."))
                .when(orderService).shipOrder(2L);

        ConcurrentOrderProcessor.BatchProcessingReport report =
                processor.shipOrdersConcurrently(List.of(1L, 2L));

        assertThat(report.getTotalSubmitted()).isEqualTo(2);
        assertThat(report.getSuccessful()).isEqualTo(1);
        assertThat(report.getFailed()).isEqualTo(1);
        assertThat(report.getOrderResults()).containsKey(2L);
        assertThat(report.getOrderResults().get(2L)).contains("FAILED");
    }

    @Test
    @DisplayName("shipOrdersConcurrently — returns empty report when input is empty")
    void shipOrdersConcurrently_returnsEmptyReport_whenInputEmpty() {
        ConcurrentOrderProcessor.BatchProcessingReport report =
                processor.shipOrdersConcurrently(List.of());

        assertThat(report.getTotalSubmitted()).isEqualTo(0);
        assertThat(report.getSuccessful()).isEqualTo(0);
        assertThat(report.getFailed()).isEqualTo(0);
        verifyNoInteractions(orderService);
    }

    // ── Helper ───────────────────────────────────────────────────────────

    private OrderResponse buildOrder(Long id) {
        return OrderResponse.builder()
                .id(id)
                .customerId(1L)
                .customerName("Test Customer")
                .placedDate(LocalDate.now())
                .shipDate(LocalDate.now().plusDays(4))
                .status(OrderStatus.PENDING)
                .items(List.of())
                .totalAmount(BigDecimal.ZERO)
                .delayed(false)
                .build();
    }
}
