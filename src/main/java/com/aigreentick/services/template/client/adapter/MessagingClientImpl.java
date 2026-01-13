package com.aigreentick.services.template.client.adapter;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.aigreentick.services.template.client.config.MessagingClientProperties;
import com.aigreentick.services.template.dto.request.template.DispatchRequestDto;
import com.aigreentick.services.template.dto.response.BroadcastDispatchResponseDto;
import com.aigreentick.services.template.dto.response.FacebookApiResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class MessagingClientImpl {
    private final WebClient.Builder webClientBuilder;
    private final MessagingClientProperties properties;

    /**
     * Dispatches messages to the messaging service for processing
     */
    public FacebookApiResponse<BroadcastDispatchResponseDto> dispatchMessage(DispatchRequestDto request) {
        log.info("Dispatching {} messages to messaging service", 
                request.getItems() != null ? request.getItems().size() : 0);

        try {
            BroadcastDispatchResponseDto response = webClientBuilder.build()
                    .post()
                    .uri(properties.getBaseUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(BroadcastDispatchResponseDto.class)
                    .block();

            if (response != null && "SUCCESS".equals(response.getStatus())) {
                log.info("Messages dispatched successfully. Total: {}, Failed: {}", 
                        response.getData().getTotalDispatched(), 
                        response.getData().getFailedCount());
                return FacebookApiResponse.success(response, 200);
            } else {
                String errorMsg = response != null ? response.getMessage() : "Unknown error";
                log.error("Failed to dispatch messages: {}", errorMsg);
                return FacebookApiResponse.error(errorMsg, 500);
            }

        } catch (Exception ex) {
            log.error("Failed to dispatch messages to messaging service", ex);
            return FacebookApiResponse.error("Failed to dispatch messages: " + ex.getMessage(), 500);
        }
    }
}