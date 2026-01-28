package com.aigreentick.services.template.schedular;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.aigreentick.services.template.model.broadcast.Broadcast;
import com.aigreentick.services.template.service.impl.broadcast.BroadcastServiceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledBroadcastProcessor {
    private final BroadcastServiceImpl broadcastServiceImpl;

    private final ScheduledBroadcastExecutor executor;
    private final BlockingQueue<Broadcast> broadcastQueue = new LinkedBlockingQueue<>();

    @Scheduled(fixedRate = 300000) // 5 minutes = 300,000ms
    public void pollScheduledBroadcasts() {
        log.info("=== Polling for scheduled broadcasts ===");

        Integer lookbackMinutes = 10;

        Integer lookaheadMinutes = 2;

        try {
            // Fetch pending broadcasts from database
            List<Broadcast> pendingBroadcasts = broadcastServiceImpl
                    .getPendingScheduledBroadcasts(lookbackMinutes, lookaheadMinutes);

            if (pendingBroadcasts.isEmpty()) {
                return;
            }

            log.info("Found {} scheduled broadcasts in time window", pendingBroadcasts.size());

            // Add to queue (avoiding duplicates)
            int added = 0;
            for (Broadcast broadcast : pendingBroadcasts) {
                // Check if already in queue (avoid duplicates)
                if (!isAlreadyInQueue(broadcast.getId())) {
                    boolean success = broadcastQueue.offer(broadcast);
                    if (success) {
                        added++;
                        log.debug("Added broadcast {} to queue, scheduled at: {}",
                                broadcast.getId(), broadcast.getScheduleAt());
                    } else {
                        log.warn("Failed to add broadcast {} to queue (queue full?)",
                                broadcast.getId());
                    }
                }
            }

            log.info("Added {} new broadcasts to queue. Queue size: {}",
                    added, broadcastQueue.size());
        } catch (Exception e) {
            log.error("Error polling scheduled broadcasts", e);
        }
    }

    @Scheduled(fixedRate = 1000) // 1 second = 1,000ms
    public void processQueuedBroadcasts() {
        if (broadcastQueue.isEmpty()) {
            return; // No broadcasts to process
        }

        LocalDateTime now = LocalDateTime.now();
        int processed = 0;

        // Process all broadcasts whose time has come
        // Peek instead of poll to avoid removing items we can't process yet
        while (!broadcastQueue.isEmpty()) {
            Broadcast broadcast = broadcastQueue.peek(); // Look at head without removing

            if (broadcast == null) {
                break;
            }

            // Check if broadcast time has arrived
            if (broadcast.getScheduleAt() != null &&
                    broadcast.getScheduleAt().isAfter(now)) {
                // time not came yet not need to check
                break;
            }

            // Remove from queue and process
            broadcastQueue.poll(); // Now actually remove it

            log.info("Processing scheduled broadcast: {} (scheduled at: {})",
                    broadcast.getId(), broadcast.getScheduleAt());

            try {
                // Delegate to executor based on broadcast source
                executor.executeBroadcast(broadcast);
                processed++;

            } catch (Exception e) {
                log.error("Failed to execute scheduled broadcast: {}",
                        broadcast.getId(), e);
                // Consider adding to a dead-letter queue or retry mechanism
            }
        }

        if (processed > 0) {
            log.info("Processed {} scheduled broadcasts. Remaining in queue: {}",
                    processed, broadcastQueue.size());
        }
    }

    /**
     * Check if broadcast is already in queue to avoid duplicates.
     */
    private boolean isAlreadyInQueue(Long broadcastId) {
        return broadcastQueue.stream()
                .anyMatch(b -> b.getId().equals(broadcastId));
    }

    /**
     * Get current queue size (for monitoring/debugging).
     */
    public int getQueueSize() {
        return broadcastQueue.size();
    }

    /**
     * Clear queue (for testing/admin purposes).
     */
    public void clearQueue() {
        broadcastQueue.clear();
        log.warn("Broadcast queue cleared manually");
    }
}
