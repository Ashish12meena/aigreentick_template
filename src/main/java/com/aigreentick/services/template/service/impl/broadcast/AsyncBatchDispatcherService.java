package com.aigreentick.services.template.service.impl.broadcast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.aigreentick.services.template.client.adapter.MessagingClientImpl;

import com.aigreentick.services.template.dto.request.WhatsappAccountInfoDto;
import com.aigreentick.services.template.dto.request.template.BroadcastDispatchItemDto;
import com.aigreentick.services.template.dto.request.template.DispatchRequestDto;
import com.aigreentick.services.template.dto.response.broadcast.BroadcastDispatchResponseDto;
import com.aigreentick.services.template.dto.response.common.FacebookApiResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Async batch dispatcher for WhatsApp messages.
 * 
 * Dispatches message chunks concurrently using CompletableFuture.
 * Does not wait for previous chunks to complete before starting next chunk.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AsyncBatchDispatcherService {

    private final MessagingClientImpl messagingClient;

    @Value("${broadcast.dispatch-chunk-size:100}")
    private int dispatchChunkSize;

    /**
     * Dispatch all items asynchronously in chunks.
     * Fire-and-forget approach - doesn't wait for previous chunk to complete.
     * 
     * @param allItems    Pre-built dispatch items (already serialized)
     * @param accountInfo WhatsApp account info
     * @param broadcastId Broadcast ID for logging
     * @return CompletableFuture that completes when all chunks are submitted
     */
    public CompletableFuture<Void> dispatchAsync(
            List<BroadcastDispatchItemDto> allItems,
            WhatsappAccountInfoDto accountInfo,
            Long broadcastId) {

        log.info("Starting async dispatch for {} pre-built items in chunks of {}",
                allItems.size(), dispatchChunkSize);

        // Split into chunks
        List<List<BroadcastDispatchItemDto>> chunks = partitionList(allItems, dispatchChunkSize);
        int totalChunks = chunks.size();

        // Track progress
        AtomicInteger completedChunks = new AtomicInteger(0);
        AtomicInteger totalDispatched = new AtomicInteger(0);
        AtomicInteger totalFailed = new AtomicInteger(0);

        // Create futures for all chunks (fire them all immediately)
        List<CompletableFuture<ChunkResult>> chunkFutures = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            int chunkNum = i + 1;
            List<BroadcastDispatchItemDto> chunk = chunks.get(i);

            CompletableFuture<ChunkResult> future = dispatchChunkAsync(
                    chunk, accountInfo, chunkNum, totalChunks, broadcastId);

            chunkFutures.add(future);
        }

        // Combine all futures and return Void
        return CompletableFuture.allOf(chunkFutures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    // Aggregate results
                    for (CompletableFuture<ChunkResult> future : chunkFutures) {
                        try {
                            ChunkResult result = future.join();
                            totalDispatched.addAndGet(result.dispatched());
                            totalFailed.addAndGet(result.failed());
                            completedChunks.incrementAndGet();
                        } catch (Exception e) {
                            log.error("Error collecting chunk result", e);
                            totalFailed.addAndGet(dispatchChunkSize);
                        }
                    }

                    log.info("=== Dispatch Summary for broadcastId: {} ===", broadcastId);
                    log.info("Total Dispatched: {}", totalDispatched.get());
                    log.info("Total Failed: {}", totalFailed.get());
                    log.info("Chunks Completed: {}/{}", completedChunks.get(), totalChunks);
                })
                .exceptionally(ex -> {
                    log.error("Fatal error during dispatch aggregation for broadcastId: {}", broadcastId, ex);
                    return null;
                });
    }

    /**
     * Dispatch a single chunk asynchronously.
     * Each chunk runs independently - no waiting for others.
     */
    @Async("messageDispatchExecutor")
    public CompletableFuture<ChunkResult> dispatchChunkAsync(
            List<BroadcastDispatchItemDto> chunk,
            WhatsappAccountInfoDto accountInfo,
            int chunkNum,
            int totalChunks,
            Long broadcastId) {

        return CompletableFuture.supplyAsync(() -> {
            String threadName = Thread.currentThread().getName();
            log.info("[{}] Processing chunk {}/{} with {} pre-built items for broadcastId: {}",
                    threadName, chunkNum, totalChunks, chunk.size(), broadcastId);

            try {
                // Items are already serialized - just send to messaging service
                DispatchRequestDto dispatchRequest = DispatchRequestDto.builder()
                        .items(chunk)
                        .accountInfo(accountInfo)
                        .build();

                FacebookApiResponse<BroadcastDispatchResponseDto> response = messagingClient
                        .dispatchMessage(dispatchRequest);

                if (response.isSuccess() && response.getData() != null
                        && response.getData().getData() != null) {

                    int dispatched = response.getData().getData().getTotalDispatched();
                    int failed = response.getData().getData().getFailedCount();

                    log.info("[{}] Chunk {}/{} completed - Dispatched: {}, Failed: {}",
                            threadName, chunkNum, totalChunks, dispatched, failed);

                    return new ChunkResult(dispatched, failed, chunkNum);
                } else {
                    log.error("[{}] Chunk {}/{} failed: {}",
                            threadName, chunkNum, totalChunks, response.getErrorMessage());
                    return new ChunkResult(0, chunk.size(), chunkNum);
                }

            } catch (Exception e) {
                log.error("[{}] Chunk {}/{} threw exception", threadName, chunkNum, totalChunks, e);
                return new ChunkResult(0, chunk.size(), chunkNum);
            }
        });
    }

    /**
     * Partition list into chunks
     */
    private <T> List<List<T>> partitionList(List<T> list, int chunkSize) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, list.size());
            partitions.add(list.subList(i, end));
        }
        return partitions;
    }

    /**
     * Result record for a single chunk
     */
    public record ChunkResult(int dispatched, int failed, int chunkNumber) {
    }

}