package com.psl.oms.async;

import com.psl.oms.dto.response.BillResponse;
import com.psl.oms.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * BillGeneratorService — asynchronous bill generation using @Async + CompletableFuture.
 *
 * ── Why a separate class from OrderService? ──────────────────────────────────
 * Spring's @Async works by wrapping the bean in a proxy. The proxy intercepts
 * the method call and submits it to the configured thread pool. This proxy only
 * works on calls that go THROUGH the proxy — i.e., calls from outside the class.
 *
 * If generateBillAsync() lived inside OrderService and was called from another
 * method in OrderService, it would bypass the proxy and run synchronously on the
 * same thread. By placing the @Async method in a SEPARATE @Service, we guarantee
 * every call goes through the Spring proxy.
 *
 * ── How the async call chain works ──────────────────────────────────────────
 *
 *   HTTP thread (Tomcat)
 *       │
 *       ├─ OrderController.getBillAsync(id)
 *       │       │
 *       │       ├─ BillGeneratorService.generateBillAsync(id)   ← returns immediately
 *       │       │       │
 *       │       │       └─ submits task to omsTaskExecutor pool
 *       │       │
 *       │       └─ returns AsyncBillResponse { status: "PROCESSING" }   ← HTTP 202 back to client
 *       │
 *       └─ (HTTP thread freed — handles next request)
 *
 *   omsTaskExecutor thread (background)
 *       │
 *       └─ OrderService.generateBill(id)   ← runs in its own @Transactional read-only tx
 *               │
 *               └─ logs the completed BillResponse
 *
 * ── CompletableFuture<BillResponse> as return type ───────────────────────────
 * Returning CompletableFuture<BillResponse> instead of void gives callers the option to:
 *   - Chain .thenAccept() callbacks
 *   - Call .get() to block and wait (useful in tests)
 *   - Register .exceptionally() handlers for error logging
 *
 * The controller does NOT call .get() — it fires and forgets.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BillGeneratorService {

    private final OrderService orderService;

    /**
     * Generates a bill for the given order asynchronously.
     *
     * This method returns to the caller immediately. Actual bill generation
     * runs on a thread from the "omsTaskExecutor" pool.
     *
     * @Async("omsTaskExecutor") — the explicit pool name prevents Spring from
     * accidentally using a different executor if more pools are added later.
     *
     * The method is public because Spring's proxy mechanism requires it.
     * Private @Async methods are silently ignored by the proxy.
     *
     * @param orderId the order to generate the bill for
     * @return a CompletableFuture that completes with the BillResponse when done,
     *         or completes exceptionally if the order doesn't exist
     */
    @Async("omsTaskExecutor")
    public CompletableFuture<BillResponse> generateBillAsync(Long orderId) {
        String threadName = Thread.currentThread().getName();
        log.info("[ASYNC] Bill generation started — orderId={}, thread={}",
                orderId, threadName);

        try {
            // Simulate a brief processing delay to make the async behaviour
            // observable in logs (remove in production).
            simulateProcessingDelay(200);

            BillResponse bill = orderService.generateBill(orderId);

            log.info("[ASYNC] Bill generation complete — orderId={}, customer='{}', total={}, thread={}",
                    orderId,
                    bill.getCustomerName(),
                    bill.getGrandTotal(),
                    threadName);

            return CompletableFuture.completedFuture(bill);

        } catch (Exception ex) {
            // Log the error with full context. The CompletableFuture completes
            // exceptionally so any registered .exceptionally() handlers can react.
            log.error("[ASYNC] Bill generation failed — orderId={}, reason={}, thread={}",
                    orderId, ex.getMessage(), threadName, ex);

            return CompletableFuture.failedFuture(ex);
        }
    }

    /**
     * Simulates processing time to make the async behaviour visible in logs and tests.
     *
     * In a real system this delay would not exist — the "work" is the actual DB queries
     * and computation inside OrderService.generateBill(). Removed in production via
     * a configuration property or simply deleting this call.
     *
     * @param millis how long to sleep
     */
    private void simulateProcessingDelay(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            // Restore interrupt flag — important for thread pool hygiene.
            // If we swallow the interrupt without restoring the flag, the thread
            // pool may not shut down cleanly on application stop.
            Thread.currentThread().interrupt();
            log.warn("[ASYNC] Bill generation interrupted during delay — orderId processing continuing");
        }
    }
}
