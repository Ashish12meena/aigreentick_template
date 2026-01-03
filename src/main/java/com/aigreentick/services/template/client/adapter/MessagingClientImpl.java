package com.aigreentick.services.template.client.adapter;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.aigreentick.services.template.client.config.MessagingClientProperties;
import com.aigreentick.services.template.dto.request.DispatchRequestDto;
import com.aigreentick.services.template.dto.response.FacebookApiResponse;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class MessagingClientImpl {
    private final WebClient.Builder webClientBuilder;
    private final MessagingClientProperties properties;

    /**
     * Dispatches a message to the messaging service for processing
     */
    public FacebookApiResponse<JsonNode> dispatchMessage(DispatchRequestDto request) {
        // log.info("Dispatching message for broadcastId: {}, mobile: {}", 
        //         request.getBroadcastId(), request.getMobileNumber());

        try {
            JsonNode response = webClientBuilder.build()
                    .post()
                    .uri(properties.getBaseUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            // log.debug("Message dispatched successfully for broadcastId: {}", request.getBroadcastId());
            return FacebookApiResponse.success(response, 200);

        } catch (Exception ex) {
            // log.error("Failed to dispatch message for broadcastId: {}", request.getBroadcastId(), ex);
            return FacebookApiResponse.error("Failed to dispatch message: " + ex.getMessage(), 500);
        }
    }
}