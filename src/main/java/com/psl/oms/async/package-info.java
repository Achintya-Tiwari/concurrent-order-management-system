/**
 * Async layer — background tasks, scheduling, and concurrent processing.
 *
 * Contains:
 *   - BillGeneratorService       — @Async bill generation via CompletableFuture
 *   - DelayedOrderScheduler      — @Scheduled nightly delayed-order scan
 *   - ConcurrentOrderProcessor   — ExecutorService-based concurrent batch processing
 */
package com.psl.oms.async;
