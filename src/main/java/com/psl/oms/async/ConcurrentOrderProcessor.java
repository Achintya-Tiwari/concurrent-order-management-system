package com.psl.oms.async;

import com.psl.oms.dto.response.OrderResponse;
import com.psl.oms.service.OrderService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ConcurrentOrderProcessor — demonstrates controlled concurrent order processing
 * using a raw Java ExecutorService and CompletableFuture chaining.
 *
 * ── Purpose ───────────────────────────────────────────────────────────────────
 * This class shows how a batch of orders can be processed concurrently rather
 * than sequentially. Real-world use cases include:
 *   - Processing end-of-day shipment confirmations for many orders at once
 *   - Generating bills for a large batch of orders in parallel
 *   - Running parallel validation checks before bulk status updates
 *
 * ── Why ExecutorService instead of @Async here? ──────────────────────────────
 * @Async is great for single-task fire-and-forget. ExecutorService is better when you:
 *   - Submit many tasks at once and want to collect ALL results
 *   - Need explicit control over the thread pool lifecycle (@PreDestroy)
 *   - Want to use invokeAll() to wait for a batch to complete
 *   - Are outside of Spring context (library code, Java SE)
 *
 * Both are shown in this project so an interviewer can see you understand the
 * tradeoffs.
 *
 * ── Thread safety of inventory updates ───────────────────────────────────────
 * The underlying OrderService.shipOrder() is @Transactional and the DB row-level
 * locking from MySQL ensures concurrent SHIP operations on different orders
 * don't interfere with each other. Each order's status is an independent row.
 *
 * ── AtomicInteger for counters ───────────────────────────────────────────────
 * successCount and failureCount use AtomicInteger instead of plain int because
 * they are written from multiple threads concurrently. Plain int is not thread-safe.
 */
@Service
@Slf4j
public class ConcurrentOrderProcessor {

    private final OrderService orderService;
    private final ExecutorService executorService;

    /**
     * Constructor injection with @Qualifier to select the correct ExecutorService bean.
     * Without @Qualifier Spring would fail to wire because there are two Executor beans
     * (omsTaskExecutor and omsConcurrentExecutor).
     */
    public ConcurrentOrderProcessor(
            OrderService orderService,
            @Qualifier("omsConcurrentExecutor") ExecutorService executorService) {
        this.orderService = orderService;
        this.executorService = executorService;
    }

    /**
     * Fetches order details for a list of order IDs concurrently.
     *
     * Each order is fetched on a separate thread from the omsConcurrentExecutor pool.
     * Results are collected once all futures complete. Failed fetches are excluded
     * from the result list; errors are logged per-order.
     *
     * This is the core concurrency pattern: submit many tasks, collect all results.
     *
     * @param orderIds the list of order IDs to fetch
     * @return list of successfully fetched OrderResponse DTOs (order not guaranteed)
     */
    public List<OrderResponse> fetchOrdersConcurrently(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            log.warn("[CONCURRENT] fetchOrdersConcurrently called with empty order list");
            return List.of();
        }

        log.info("[CONCURRENT] Starting concurrent fetch for {} orders using pool '{}'",
                orderIds.size(), "omsConcurrentExecutor");

        long startMs = System.currentTimeMillis();

        // Submit one CompletableFuture per order ID.
        // supplyAsync runs the supplier on the omsConcurrentExecutor thread pool.
        List<CompletableFuture<OrderResponse>> futures = orderIds.stream()
                .map(orderId -> CompletableFuture
                        .supplyAsync(() -> fetchSingleOrder(orderId), executorService)
                        .exceptionally(ex -> {
                            // Per-task error handling: log and return null so the batch
                            // continues even if individual orders fail.
                            log.error("[CONCURRENT] Failed to fetch order #{}: {}", orderId, ex.getMessage());
                            return null;
                        })
                )
                .toList();

        // Wait for ALL futures to complete before returning.
        // allOf returns a CompletableFuture<Void> that completes when every future is done.
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Collect non-null results (nulls came from failed futures).
        List<OrderResponse> results = futures.stream()
                .map(CompletableFuture::join)        // safe: all futures already completed above
                .filter(response -> response != null)
                .toList();

        long elapsedMs = System.currentTimeMillis() - startMs;
        log.info("[CONCURRENT] Fetch complete — {}/{} orders retrieved in {}ms",
                results.size(), orderIds.size(), elapsedMs);

        return results;
    }

    /**
     * Ships a batch of orders concurrently and returns a processing report.
     *
     * Each ship operation runs independently. The report shows which succeeded,
     * which failed, and why — useful for bulk shipment confirmation workflows.
     *
     * Concurrency safety: shipOrder() is @Transactional. Each order occupies an
     * independent DB row, so concurrent calls on different order IDs never contend
     * for the same lock.
     *
     * @param orderIds the orders to ship
     * @return a summary report of the batch operation
     */
    public BatchProcessingReport shipOrdersConcurrently(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return BatchProcessingReport.empty();
        }

        log.info("[CONCURRENT] Starting concurrent ship operation for {} orders", orderIds.size());
        long startMs = System.currentTimeMillis();

        // AtomicInteger for thread-safe counters — written from multiple worker threads.
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // ConcurrentHashMap for thread-safe result tracking.
        Map<Long, String> results = new ConcurrentHashMap<>();

        // Build one Callable per order.
        List<Callable<Void>> tasks = orderIds.stream()
                .<Callable<Void>>map(orderId -> () -> {
                    String threadName = Thread.currentThread().getName();
                    log.info("[CONCURRENT] Shipping order #{} on thread '{}'", orderId, threadName);

                    try {
                        orderService.shipOrder(orderId);
                        successCount.incrementAndGet();
                        results.put(orderId, "SHIPPED");
                        log.info("[CONCURRENT] Order #{} shipped successfully on thread '{}'",
                                orderId, threadName);
                    } catch (Exception ex) {
                        failureCount.incrementAndGet();
                        results.put(orderId, "FAILED: " + ex.getMessage());
                        log.error("[CONCURRENT] Failed to ship order #{} on thread '{}': {}",
                                orderId, threadName, ex.getMessage());
                    }
                    return null;
                })
                .toList();

        try {
            // invokeAll blocks until every task finishes (or the pool shuts down).
            // Unlike submitting CompletableFutures individually, invokeAll gives
            // us a clean "wait for all" with a single call.
            List<Future<Void>> futures = executorService.invokeAll(tasks);

            // Check for cancellation (invokeAll can cancel tasks on interruption).
            for (Future<Void> future : futures) {
                if (future.isCancelled()) {
                    log.warn("[CONCURRENT] One or more ship tasks were cancelled");
                    break;
                }
            }

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); // restore interrupt flag
            log.error("[CONCURRENT] Batch ship operation was interrupted after {}ms",
                    System.currentTimeMillis() - startMs, ex);
        }

        long elapsedMs = System.currentTimeMillis() - startMs;
        BatchProcessingReport report = BatchProcessingReport.builder()
                .totalSubmitted(orderIds.size())
                .successful(successCount.get())
                .failed(failureCount.get())
                .elapsedMs(elapsedMs)
                .orderResults(results)
                .build();

        log.info("[CONCURRENT] Batch ship complete — total={}, success={}, failed={}, elapsed={}ms",
                report.getTotalSubmitted(), report.getSuccessful(),
                report.getFailed(), report.getElapsedMs());

        return report;
    }

    /**
     * Fetches a single order, wrapping the service call with thread-level logging.
     * Called from within the CompletableFuture.supplyAsync() lambdas above.
     */
    private OrderResponse fetchSingleOrder(Long orderId) {
        log.debug("[CONCURRENT] Fetching order #{} on thread '{}'",
                orderId, Thread.currentThread().getName());
        return orderService.getOrderById(orderId);
    }

    /**
     * Graceful shutdown of the ExecutorService on application stop.
     *
     * Without this, the JVM might exit while tasks are still running.
     * The two-phase shutdown (shutdown + awaitTermination + shutdownNow) is
     * the standard Java pattern recommended in the ExecutorService Javadoc.
     */
    @PreDestroy
    public void shutdownExecutor() {
        log.info("[CONCURRENT] Shutting down omsConcurrentExecutor...");
        executorService.shutdown(); // stop accepting new tasks

        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("[CONCURRENT] Executor did not terminate in 30s — forcing shutdown");
                executorService.shutdownNow(); // interrupt running tasks
            } else {
                log.info("[CONCURRENT] omsConcurrentExecutor shut down cleanly");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
            log.error("[CONCURRENT] Shutdown interrupted", ex);
        }
    }

    // ── Inner record: BatchProcessingReport ─────────────────────────────────

    /**
     * Immutable report returned after a concurrent batch operation.
     * Records are a Java 16+ feature — concise, immutable, with auto-generated
     * equals/hashCode/toString. Annotated with Lombok @Builder for construction.
     */
    @lombok.Builder
    @lombok.Getter
    @lombok.ToString
    public static class BatchProcessingReport {

        private final int totalSubmitted;
        private final int successful;
        private final int failed;
        private final long elapsedMs;
        private final Map<Long, String> orderResults;

        static BatchProcessingReport empty() {
            return BatchProcessingReport.builder()
                    .totalSubmitted(0)
                    .successful(0)
                    .failed(0)
                    .elapsedMs(0)
                    .orderResults(Map.of())
                    .build();
        }
    }
}
