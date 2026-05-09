package com.psl.oms.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AsyncConfig — thread pool configuration for all async and concurrent work in the OMS.
 *
 * ── Why a dedicated config instead of Spring's default? ─────────────────────
 * Spring's default async executor is SimpleAsyncTaskExecutor, which creates a brand-new
 * OS thread for every @Async call. Under load this causes thread explosion, OOM errors,
 * and no backpressure. A bounded thread pool gives us:
 *
 *   - Controlled resource usage (capped thread count)
 *   - A bounded work queue (backpressure when all threads are busy)
 *   - Graceful shutdown (waits for in-flight tasks to complete)
 *   - Named threads (visible in thread dumps: "oms-async-1", "oms-concurrent-1")
 *
 * ── Two pools, two concerns ──────────────────────────────────────────────────
 * We define two separate executors:
 *
 *   omsTaskExecutor      — Spring @Async tasks (bill generation triggered by HTTP requests).
 *                          Small pool (2–5 threads), large queue (100). Most requests complete
 *                          quickly; the queue absorbs bursts without spawning extra threads.
 *
 *   omsConcurrentExecutor — ExecutorService for the ConcurrentOrderProcessor demo.
 *                           Fixed pool, no queue. Illustrates direct ExecutorService usage
 *                           (not Spring-managed) — a common interview topic.
 *
 * ── Interview talking points ─────────────────────────────────────────────────
 * Q: Why not just use @Async everywhere?
 * A: @Async is convenient for fire-and-forget tasks. ExecutorService gives you more control:
 *    you can submit Callable tasks, get Futures, cancel tasks, and shut down explicitly.
 *    Both have their place; this project demonstrates both.
 */
@Configuration
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    // ── Pool sizing constants ────────────────────────────────────────────
    // Centralised so they can be overridden via @ConfigurationProperties in a later phase.

    private static final int ASYNC_CORE_POOL_SIZE    = 2;
    private static final int ASYNC_MAX_POOL_SIZE      = 5;
    private static final int ASYNC_QUEUE_CAPACITY     = 100;
    private static final int ASYNC_TERMINATION_SEC    = 30;

    private static final int CONCURRENT_POOL_SIZE     = 4;

    /**
     * omsTaskExecutor — Spring-managed @Async thread pool.
     *
     * Used by BillGeneratorService.generateBillAsync(). Referenced by name in
     * @Async("omsTaskExecutor") to prevent Spring from picking the wrong executor
     * if multiple executor beans exist.
     *
     * ThreadPoolTaskExecutor wraps Java's ThreadPoolExecutor and adds Spring
     * lifecycle integration (graceful shutdown via setWaitForTasksToCompleteOnShutdown).
     *
     * Pool behaviour:
     *   - Threads 1..corePoolSize are always alive (idle but ready)
     *   - New tasks go to the queue when core threads are busy
     *   - New threads beyond corePoolSize are spawned only when the queue is full
     *   - Tasks are rejected (CallerRunsPolicy) when queue and max pool are both full
     */
    @Bean(name = "omsTaskExecutor")
    public Executor omsTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(ASYNC_CORE_POOL_SIZE);
        executor.setMaxPoolSize(ASYNC_MAX_POOL_SIZE);
        executor.setQueueCapacity(ASYNC_QUEUE_CAPACITY);
        executor.setThreadNamePrefix("oms-async-");

        // When app shuts down, wait for in-flight tasks instead of killing them mid-work.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(ASYNC_TERMINATION_SEC);

        // If pool + queue are both saturated, run the task on the caller's thread.
        // This provides backpressure without dropping requests — better than throwing RejectedExecutionException.
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());

        executor.initialize();

        log.info("omsTaskExecutor ready — core={}, max={}, queue={}",
                ASYNC_CORE_POOL_SIZE, ASYNC_MAX_POOL_SIZE, ASYNC_QUEUE_CAPACITY);
        return executor;
    }

    /**
     * omsConcurrentExecutor — plain Java ExecutorService for the ConcurrentOrderProcessor.
     *
     * This is deliberately NOT a ThreadPoolTaskExecutor — it illustrates raw
     * ExecutorService usage, which is what you would use outside of Spring
     * (in a library, a Java SE application, or when you need Future<T> directly).
     *
     * Fixed thread pool: exactly CONCURRENT_POOL_SIZE threads, no task queue growth.
     * Shutting the application context down calls @PreDestroy on the ConcurrentOrderProcessor,
     * which shuts this pool down cleanly.
     *
     * Note: this bean is injected by name to avoid ambiguity with omsTaskExecutor.
     */
    @Bean(name = "omsConcurrentExecutor")
    public ExecutorService omsConcurrentExecutor() {
        ExecutorService executor = Executors.newFixedThreadPool(
                CONCURRENT_POOL_SIZE,
                runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("oms-concurrent-" + thread.getId());
                    thread.setDaemon(false); // non-daemon: JVM waits for these to finish on shutdown
                    return thread;
                }
        );
        log.info("omsConcurrentExecutor ready — fixed pool size={}", CONCURRENT_POOL_SIZE);
        return executor;
    }

    /**
     * Returns omsTaskExecutor as the default executor for @Async methods
     * that don't specify an executor name.
     *
     * Implementing AsyncConfigurer means any bare @Async annotation (without
     * a name) will use our bounded pool instead of SimpleAsyncTaskExecutor.
     */
    @Override
    public Executor getAsyncExecutor() {
        return omsTaskExecutor();
    }
}
