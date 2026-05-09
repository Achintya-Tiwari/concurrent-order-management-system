package com.psl.oms.async;

import com.psl.oms.dto.response.BillResponse;
import com.psl.oms.dto.response.OrderItemResponse;
import com.psl.oms.exception.ResourceNotFoundException;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * BillGeneratorServiceTest — unit tests for async bill generation.
 *
 * IMPORTANT: @Async is a Spring proxy concern — it does NOT fire when the bean
 * is instantiated directly via new or via Mockito @InjectMocks. This is actually
 * what we want in unit tests: we test the METHOD LOGIC without needing a real
 * thread pool. The test runs the method synchronously and verifies the return value.
 *
 * To test the actual async behaviour (does it really run on a background thread?),
 * a @SpringBootTest integration test with a real thread pool is needed.
 * That is shown in BillGeneratorServiceIntegrationTest (Phase 4 scope).
 *
 * Pattern: mock OrderService → call generateBillAsync() → assert on the returned future.
 */
@ExtendWith(MockitoExtension.class)
class BillGeneratorServiceTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private BillGeneratorService billGeneratorService;

    private BillResponse buildSampleBill(Long orderId) {
        return BillResponse.builder()
                .orderId(orderId)
                .customerId(1L)
                .customerName("Alice Smith")
                .customerAddress("10 Test Lane")
                .customerCellNumber("9876543210")
                .placedDate(LocalDate.now())
                .actualShipDate(LocalDate.now())
                .items(List.of(
                        OrderItemResponse.builder()
                                .id(1L)
                                .stockItemId(10L)
                                .stockItemName("Widget")
                                .quantity(2)
                                .unitPrice(new BigDecimal("25.00"))
                                .subtotal(new BigDecimal("50.00"))
                                .build()
                ))
                .grandTotal(new BigDecimal("50.00"))
                .build();
    }

    @Test
    @DisplayName("generateBillAsync — returns completed future with BillResponse on success")
    void generateBillAsync_returnsCompletedFuture_onSuccess() throws Exception {
        BillResponse expected = buildSampleBill(1L);
        when(orderService.generateBill(1L)).thenReturn(expected);

        CompletableFuture<BillResponse> future = billGeneratorService.generateBillAsync(1L);

        // future.get() blocks until complete — fine in a unit test (runs synchronously without Spring proxy)
        assertThat(future).isNotNull();
        assertThat(future.isDone()).isTrue();

        BillResponse result = future.get();
        assertThat(result.getOrderId()).isEqualTo(1L);
        assertThat(result.getCustomerName()).isEqualTo("Alice Smith");
        assertThat(result.getGrandTotal()).isEqualByComparingTo(new BigDecimal("50.00"));

        verify(orderService).generateBill(1L);
    }

    @Test
    @DisplayName("generateBillAsync — returns failed future when order does not exist")
    void generateBillAsync_returnsFailedFuture_whenOrderNotFound() {
        when(orderService.generateBill(99L))
                .thenThrow(new ResourceNotFoundException("Order", "id", 99L));

        CompletableFuture<BillResponse> future = billGeneratorService.generateBillAsync(99L);

        assertThat(future).isNotNull();
        assertThat(future.isCompletedExceptionally()).isTrue();

        // Assert the underlying cause is the ResourceNotFoundException we expect
        assertThatThrownBy(future::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("generateBillAsync — calls orderService.generateBill exactly once")
    void generateBillAsync_callsGenerateBill_exactlyOnce() throws Exception {
        BillResponse bill = buildSampleBill(5L);
        when(orderService.generateBill(5L)).thenReturn(bill);

        billGeneratorService.generateBillAsync(5L).get();

        // Verify the service is only called once — no double-processing
        verify(orderService, times(1)).generateBill(5L);
    }

    @Test
    @DisplayName("generateBillAsync — grand total matches sum of item subtotals")
    void generateBillAsync_grandTotal_matchesItemSubtotals() throws Exception {
        BillResponse bill = buildSampleBill(2L);
        when(orderService.generateBill(2L)).thenReturn(bill);

        BillResponse result = billGeneratorService.generateBillAsync(2L).get();

        BigDecimal expectedTotal = result.getItems().stream()
                .map(OrderItemResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(result.getGrandTotal()).isEqualByComparingTo(expectedTotal);
    }
}
