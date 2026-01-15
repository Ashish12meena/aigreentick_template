package com.aigreentick.services.template.service.impl.broadcast;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.aigreentick.services.template.client.adapter.MessagingClientImpl;
import com.aigreentick.services.template.dto.build.MessageRequest;
import com.aigreentick.services.template.dto.build.TemplateDto;
import com.aigreentick.services.template.dto.request.WhatsappAccountInfoDto;
import com.aigreentick.services.template.dto.request.template.BroadcastDispatchItemDto;
import com.aigreentick.services.template.dto.request.template.DispatchRequestDto;
import com.aigreentick.services.template.dto.request.template.SendTemplateRequestDto;
import com.aigreentick.services.template.dto.response.broadcast.BroadcastDispatchResponseDto;
import com.aigreentick.services.template.dto.response.common.FacebookApiResponse;
import com.aigreentick.services.template.service.impl.template.TemplateBuilderServiceImpl;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    private final TemplateBuilderServiceImpl templateBuilderService;
    private final MessagingClientImpl messagingClient;
    private final ObjectMapper objectMapper;

    @Value("${broadcast.dispatch-chunk-size:100}")
    private int dispatchChunkSize;

    /**
     * Dispatch messages in concurrent batches using CompletableFuture.
     * 
     * @return CompletableFuture<DispatchSummary> with total dispatched/failed counts
     */
    public CompletableFuture<DispatchSummary> dispatchMessagesAsync(
            Long userId,
            List<String> validNumbers,
            TemplateDto templateDto,
            SendTemplateRequestDto request,
            Long broadcastId,
            WhatsappAccountInfoDto accountInfo) {

        log.info("=== Starting async dispatch for {} numbers in chunks of {} ===", 
                validNumbers.size(), dispatchChunkSize);

        // Split numbers into chunks
        List<List<String>> chunks = partitionList(validNumbers, dispatchChunkSize);
        int totalChunks = chunks.size();
        
        log.info("Created {} chunks for async processing", totalChunks);

        // Track progress
        AtomicInteger chunkCounter = new AtomicInteger(0);
        AtomicInteger totalDispatched = new AtomicInteger(0);
        AtomicInteger totalFailed = new AtomicInteger(0);

        // Create async tasks for each chunk
        List<CompletableFuture<ChunkResult>> chunkFutures = chunks.stream()
                .map(chunk -> dispatchChunkAsync(
                        userId, 
                        chunk, 
                        templateDto, 
                        request, 
                        broadcastId, 
                        accountInfo,
                        chunkCounter.incrementAndGet(),
                        totalChunks))
                .collect(Collectors.toList());

        // Combine all futures and aggregate results
        return CompletableFuture.allOf(chunkFutures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    // Collect results from all chunks
                    chunkFutures.forEach(future -> {
                        try {
                            ChunkResult result = future.join();
                            totalDispatched.addAndGet(result.dispatched());
                            totalFailed.addAndGet(result.failed());
                        } catch (Exception e) {
                            log.error("Error collecting chunk result", e);
                        }
                    });

                    DispatchSummary summary = new DispatchSummary(
                            totalDispatched.get(), 
                            totalFailed.get(),
                            validNumbers.size()
                    );

                    log.info("=== Async dispatch completed - Dispatched: {}, Failed: {}, Total: {} ===",
                            summary.dispatched(), summary.failed(), summary.total());

                    return summary;
                })
                .exceptionally(ex -> {
                    log.error("Fatal error during async dispatch", ex);
                    return new DispatchSummary(0, validNumbers.size(), validNumbers.size());
                });
    }

    /**
     * Dispatch a single chunk asynchronously.
     * Annotated with @Async to run in separate thread pool.
     */
    @Async("messageDispatchExecutor")
    public CompletableFuture<ChunkResult> dispatchChunkAsync(
            Long userId,
            List<String> phoneNumbers,
            TemplateDto templateDto,
            SendTemplateRequestDto request,
            Long broadcastId,
            WhatsappAccountInfoDto accountInfo,
            int chunkNumber,
            int totalChunks) {

        return CompletableFuture.supplyAsync(() -> {
            log.info("Thread [{}] - Processing chunk {}/{} with {} numbers",
                    Thread.currentThread().getName(), chunkNumber, totalChunks, phoneNumbers.size());

            try {
                // Build message requests
                List<MessageRequest> messageRequests = templateBuilderService.buildSendableTemplates(
                        userId, phoneNumbers, templateDto, request);

                // Convert to dispatch items
                List<BroadcastDispatchItemDto> dispatchItems = buildDispatchItems(
                        messageRequests, broadcastId);

                if (dispatchItems.isEmpty()) {
                    log.warn("Chunk {}/{} - No dispatch items built", chunkNumber, totalChunks);
                    return new ChunkResult(0, phoneNumbers.size(), chunkNumber);
                }

                // Dispatch to messaging service
                DispatchRequestDto dispatchRequest = DispatchRequestDto.builder()
                        .items(dispatchItems)
                        .accountInfo(accountInfo)
                        .build();

                FacebookApiResponse<BroadcastDispatchResponseDto> response = 
                        messagingClient.dispatchMessage(dispatchRequest);

                if (response.isSuccess() && response.getData() != null && 
                        response.getData().getData() != null) {
                    
                    int dispatched = response.getData().getData().getTotalDispatched();
                    int failed = response.getData().getData().getFailedCount();

                    log.info("Chunk {}/{} completed - Dispatched: {}, Failed: {}",
                            chunkNumber, totalChunks, dispatched, failed);

                    return new ChunkResult(dispatched, failed, chunkNumber);
                } else {
                    log.error("Chunk {}/{} failed: {}", 
                            chunkNumber, totalChunks, response.getErrorMessage());
                    return new ChunkResult(0, dispatchItems.size(), chunkNumber);
                }

            } catch (Exception e) {
                log.error("Chunk {}/{} threw exception", chunkNumber, totalChunks, e);
                return new ChunkResult(0, phoneNumbers.size(), chunkNumber);
            }
        });
    }

    /**
     * Build dispatch items from message requests
     */
    private List<BroadcastDispatchItemDto> buildDispatchItems(
            List<MessageRequest> messageRequests,
            Long broadcastId) {

        return messageRequests.stream()
                .map(msg -> {
                    try {
                        String payload = objectMapper
                                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                                .writeValueAsString(msg);

                        return BroadcastDispatchItemDto.builder()
                                .broadcastId(broadcastId)
                                .mobileNo(msg.getTo())
                                .payload(payload)
                                .build();

                    } catch (Exception e) {
                        log.error("Error serializing message for {}: {}", msg.getTo(), e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Partition list into chunks of specified size
     */
    private <T> List<List<T>> partitionList(List<T> list, int chunkSize) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, list.size());
            partitions.add(list.subList(i, end));
        }
        return partitions;
    }

    // ==================== RESULT RECORDS ====================

    /**
     * Result for a single chunk dispatch
     */
    public record ChunkResult(int dispatched, int failed, int chunkNumber) {}

    /**
     * Overall dispatch summary
     */
    public record DispatchSummary(int dispatched, int failed, int total) {}
}