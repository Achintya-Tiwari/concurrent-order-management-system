package com.psl.oms.async;

import com.psl.oms.dto.response.OrderResponse;
import com.psl.oms.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DelayedOrderScheduler — nightly automated scan for orders that have missed their SLA.
 *
 * ── What it does ─────────────────────────────────────────────────────────────
 * Every night at midnight (configurable via cron expression) this scheduler:
 *   1. Calls OrderService.getDelayedOrders() — the same logic used by GET /api/orders/delayed
 *   2. Logs a summary: how many delayed orders exist and which order IDs they are
 *   3. Returns — currently log-only; Phase 4 could add email alerts or a DB status column
 *
 * ── @Scheduled cron syntax ───────────────────────────────────────────────────
 * "0 0 0 * * *" means:
 *   0  → second 0
 *   0  → minute 0
 *   0  → hour 0 (midnight)
 *   *  → any day of month
 *   *  → any month
 *   *  → any day of week
 *
 * The cron is externalised to application.yml via ${oms.scheduler.delayed-orders.cron}
 * so it can be changed per environment without recompiling (e.g., run every minute
 * in dev for testing: "0 * * * * *").
 *
 * ── @EnableScheduling ────────────────────────────────────────────────────────
 * OmsApplication already carries @EnableScheduling — no additional annotation needed here.
 *
 * ── Thread model ─────────────────────────────────────────────────────────────
 * By default, Spring runs ALL @Scheduled tasks on a single shared thread. This means:
 *   - If one scheduler takes too long, it blocks others.
 *   - Two scheduled tasks can never run at the same time by default.
 *
 * For this project (one simple scheduler) the default is fine.
 * The SchedulingConfigurer interface can be used to provide a dedicated scheduler
 * thread pool if more tasks are added.
 *
 * ── Why @Component and not @Service? ─────────────────────────────────────────
 * @Component is semantically correct for a class that has no business logic of its own —
 * it just coordinates other services. @Service implies the class contains business logic.
 * Both work; the distinction is convention.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DelayedOrderScheduler {

    private final OrderService orderService;

    /**
     * Scans for delayed orders once per day at midnight.
     *
     * Cron is read from application.yml:
     *   oms.scheduler.delayed-orders.cron=0 0 0 * * *
     *
     * fixedDelay alternative (not used here but worth knowing):
     *   @Scheduled(fixedDelayString = "${oms.scheduler.delayed-orders.fixed-delay-ms}")
     *   — runs N ms AFTER the previous run finishes (safer for long-running tasks).
     *
     * fixedRate (also not used):
     *   @Scheduled(fixedRateString = "...")
     *   — runs every N ms regardless of how long the previous run took (can stack up).
     *
     * Cron is the right choice here because we want "once at midnight" semantics,
     * not "every 86400 ms from app startup".
     */
    @Scheduled(cron = "${oms.scheduler.delayed-orders.cron:0 0 0 * * *}")
    public void scanDelayedOrders() {
        LocalDateTime scanStartedAt = LocalDateTime.now();
        log.info("[SCHEDULER] Delayed-order scan started at {}", scanStartedAt);

        try {
            List<OrderResponse> delayedOrders = orderService.getDelayedOrders();

            if (delayedOrders.isEmpty()) {
                log.info("[SCHEDULER] Scan complete — no delayed orders found. Checked at {}",
                        scanStartedAt);
                return;
            }

            // Log a brief summary first so the count is immediately visible in logs.
            log.warn("[SCHEDULER] {} delayed order(s) found — these have exceeded the 4-day SLA:",
                    delayedOrders.size());

            // Log each delayed order so the operations team has full context without
            // needing to query the database manually.
            delayedOrders.forEach(order ->
                log.warn("[SCHEDULER]   → Order #{} | Customer: '{}' | Placed: {} | SLA deadline: {}",
                        order.getId(),
                        order.getCustomerName(),
                        order.getPlacedDate(),
                        order.getShipDate())
            );

            log.warn("[SCHEDULER] Scan complete — {} delayed order(s) require attention.",
                    delayedOrders.size());

        } catch (Exception ex) {
            // Never let a scheduler crash silently — log the full exception so the
            // ops team can investigate. Spring swallows scheduler exceptions by default
            // and stops future runs if the method throws an unchecked exception.
            log.error("[SCHEDULER] Delayed-order scan failed unexpectedly at {}. " +
                      "Next run will be attempted at the next scheduled time.",
                    scanStartedAt, ex);
        }
    }
}
