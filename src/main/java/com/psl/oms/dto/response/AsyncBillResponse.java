package com.psl.oms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * AsyncBillResponse — returned immediately by GET /api/orders/{id}/bill/async.
 *
 * When a client hits the async bill endpoint, bill generation is handed off to
 * a background thread. The HTTP response returns straight away with this DTO —
 * the client doesn't wait for the bill to be assembled.
 *
 * In a full production system the client would poll a status endpoint or receive
 * a push notification (WebSocket / webhook) when the bill is ready. For this
 * portfolio project the async endpoint demonstrates the non-blocking pattern;
 * the completed BillResponse is written to the application log and could be
 * stored in a DB table or cache in a Phase 4 extension.
 *
 * Fields:
 *   orderId   — echoes back the requested order ID so the client can correlate responses.
 *   status    — always "PROCESSING" when first returned.
 *   message   — human-readable description of what is happening.
 *   requestedAt — timestamp so the client knows when the job was accepted.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AsyncBillResponse {

    private Long orderId;
    private String status;
    private String message;
    private Instant requestedAt;

    /** Convenience factory for the accepted-and-processing state. */
    public static AsyncBillResponse accepted(Long orderId) {
        return AsyncBillResponse.builder()
                .orderId(orderId)
                .status("PROCESSING")
                .message("Bill generation has been queued. The result will be logged by the server.")
                .requestedAt(Instant.now())
                .build();
    }
}
