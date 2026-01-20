package com.aigreentick.services.template.client.adapter;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import com.aigreentick.services.template.config.client.WhatsappClientProperties;
import com.aigreentick.services.template.dto.response.common.FacebookApiResponse;
import com.aigreentick.services.template.dto.response.media.UploadMediaResponse;
import com.aigreentick.services.template.dto.response.media.UploadOffsetResponse;
import com.aigreentick.services.template.dto.response.media.UploadSessionResponse;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@Slf4j
@Component
public class WhatsappClientImpl {
        private final WebClient.Builder webClientBuilder;
        private final WhatsappClientProperties properties;

        /**
         * Sends a WhatsApp template to Facebook for approval.
         */
        public FacebookApiResponse<JsonNode> createTemplate(String bodyJson, String wabaId, String accessToken) {

                if (!properties.isOutgoingEnabled()) {
                        return FacebookApiResponse.error("Outgoing requests disabled", 503);
                }

                URI uri = UriComponentsBuilder
                                .fromUriString(properties.getBaseUrl())
                                .pathSegment(properties.getApiVersion(), wabaId, "message_templates")
                                .build()
                                .toUri();

                try {
                        JsonNode response = webClientBuilder.build()
                                        .post()
                                        .uri(uri)
                                        .headers(headers -> headers.setBearerAuth(accessToken))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .bodyValue(bodyJson)
                                        .retrieve()
                                        .onStatus(HttpStatusCode::is4xxClientError, r -> r.bodyToMono(String.class)
                                                        .flatMap(errorBody -> {
                                                                log.error("Facebook API 4xx error for WABA_ID={}: {}",
                                                                                wabaId, errorBody);
                                                                return Mono.error(new RuntimeException(
                                                                                "Facebook API returned 4xx: "
                                                                                                + errorBody));
                                                        }))
                                        .onStatus(HttpStatusCode::is5xxServerError, r -> r.bodyToMono(String.class)
                                                        .flatMap(errorBody -> {
                                                                log.error("Facebook API 5xx error for WABA_ID={}: {}",
                                                                                wabaId, errorBody);
                                                                return Mono.error(new RuntimeException(
                                                                                "Facebook API returned 5xx: "
                                                                                                + errorBody));
                                                        }))
                                        .bodyToMono(JsonNode.class)
                                        .block();

                        log.info("Template sent to Facebook. WABA_ID={} Response={}", wabaId, response);
                        return FacebookApiResponse.success(response, 200);

                } catch (WebClientResponseException ex) {
                        log.error("Failed to send template. WABA_ID={} Status={} Response={}", wabaId,
                                        ex.getStatusCode().value(), ex.getResponseBodyAsString());
                        return FacebookApiResponse.error(ex.getResponseBodyAsString(), ex.getStatusCode().value());

                } catch (Exception ex) {
                        log.error("Unexpected error while sending template to Facebook. WABA_ID={}", wabaId, ex);
                        return FacebookApiResponse.error("Internal Server Error: " + ex.getMessage(), 500);
                }
        }

        /**
         * Fetches all WhatsApp templates for a given WABA.
         * Supports optional filters: status, language, category, name, limit.
         * Wrapped with Retry and CircuitBreaker for resilience.
         */
        public FacebookApiResponse<JsonNode> getAllTemplates(
                        String wabaId,
                        String accessToken,
                        Optional<String> status,
                        Optional<String> language,
                        Optional<String> category,
                        Optional<String> name,
                        Optional<Integer> limit) {

                // Build URI dynamically using properties and query params
                UriComponentsBuilder builder = UriComponentsBuilder
                                .fromUriString(properties.getBaseUrl())
                                .pathSegment(properties.getApiVersion(), wabaId, "message_templates");

                status.ifPresent(s -> builder.queryParam("status", s));
                language.ifPresent(l -> builder.queryParam("language", l));
                category.ifPresent(c -> builder.queryParam("category", c));
                name.ifPresent(n -> builder.queryParam("name", n));
                limit.ifPresent(l -> builder.queryParam("limit", l));

                URI uri = builder.build().toUri();

                try {
                        JsonNode response = webClientBuilder.build()
                                        .get()
                                        .uri(uri)
                                        .headers(headers -> headers.setBearerAuth(accessToken))
                                        .retrieve()
                                        .onStatus(HttpStatusCode::is4xxClientError,
                                                        r -> Mono.error(new RuntimeException(
                                                                        "Facebook API returned 4xx")))
                                        .onStatus(HttpStatusCode::is5xxServerError,
                                                        r -> Mono.error(new RuntimeException(
                                                                        "Facebook API returned 5xx")))
                                        .bodyToMono(JsonNode.class)
                                        .block();

                        // log.info("Fetched all templates. WABA_ID={} URI={} Response={}", wabaId, uri, response);
                        return FacebookApiResponse.success(response, 200);

                } catch (WebClientResponseException ex) {
                        log.error("Failed to fetch all templates. WABA_ID={} URI={} Status={} Response={}", wabaId, uri,
                                        ex.getStatusCode(), ex.getResponseBodyAsString());
                        return FacebookApiResponse.error(ex.getResponseBodyAsString(), ex.getStatusCode().value());

                } catch (Exception ex) {
                        log.error("Unexpected error while fetching templates. WABA_ID={} URI={}", wabaId, uri, ex);
                        return FacebookApiResponse.error("Internal Server Error: " + ex.getMessage(), 500);
                }
        }

        /**
         * Fetches a WhatsApp template by its name.
         */
        public FacebookApiResponse<JsonNode> getTemplateByName(
                        String templateName, String wabaId, String accessToken) {

                // Build URI using properties and query param
                URI uri = UriComponentsBuilder
                                .fromUriString(properties.getBaseUrl())
                                .pathSegment(properties.getApiVersion(), wabaId, "message_templates")
                                .queryParam("name", templateName)
                                .build()
                                .toUri();

                try {
                        JsonNode response = webClientBuilder.build()
                                        .get()
                                        .uri(uri)
                                        .headers(headers -> headers.setBearerAuth(accessToken))
                                        .retrieve()
                                        .onStatus(HttpStatusCode::is4xxClientError,
                                                        r -> Mono.error(new RuntimeException(
                                                                        "Facebook API returned 4xx")))
                                        .onStatus(HttpStatusCode::is5xxServerError,
                                                        r -> Mono.error(new RuntimeException(
                                                                        "Facebook API returned 5xx")))
                                        .bodyToMono(JsonNode.class)
                                        .block();

                        log.info("Fetched template by name. Name={} URI={} Response={}", templateName, uri, response);
                        return FacebookApiResponse.success(response, 200);

                } catch (WebClientResponseException ex) {
                        log.error("Failed to fetch template by name. Name={} URI={} Status={} Response={}",
                                        templateName, uri,
                                        ex.getStatusCode(), ex.getResponseBodyAsString());
                        return FacebookApiResponse.error(ex.getResponseBodyAsString(), ex.getStatusCode().value());

                } catch (Exception ex) {
                        log.error("Unexpected error while fetching template by name. Name={} URI={}", templateName, uri,
                                        ex);
                        return FacebookApiResponse.error("Internal Server Error: " + ex.getMessage(), 500);
                }
        }

        // Resumbale api's

        /**
         * Step 1: Initiates an upload session with the Facebook Graph API.
         */
        public FacebookApiResponse<UploadSessionResponse> initiateUploadSession(String fileName, long fileSize,
                        String mimeType,
                        String wabaAppId, String accessToken) {
                if (!properties.isOutgoingEnabled()) {
                        return FacebookApiResponse.error("Outgoing requests disabled", 503);
                }

                URI uri = UriComponentsBuilder
                                .fromUriString(properties.getBaseUrl())
                                .pathSegment(properties.getApiVersion(), wabaAppId, "uploads")
                                .queryParam("file_name", fileName)
                                .queryParam("file_length", fileSize)
                                .queryParam("file_type", mimeType)
                                .queryParam("access_token", accessToken)
                                .build()
                                .toUri();

                log.info("Initiating upload session: {}", uri);

                try {
                        UploadSessionResponse response = webClientBuilder.build()
                                        .post()
                                        .uri(uri)
                                        .accept(MediaType.APPLICATION_JSON)
                                        .retrieve()
                                        .onStatus(HttpStatusCode::is4xxClientError, r -> r.bodyToMono(String.class)
                                                        .flatMap(errorBody -> {
                                                                log.error("Facebook API 4xx during upload initiation for appId={}: {}",
                                                                                wabaAppId,
                                                                                errorBody);
                                                                return Mono.error(new RuntimeException(
                                                                                "Facebook API returned 4xx: "
                                                                                                + errorBody));
                                                        }))
                                        .onStatus(HttpStatusCode::is5xxServerError, r -> r.bodyToMono(String.class)
                                                        .flatMap(errorBody -> {
                                                                log.error("Facebook API 5xx during upload initiation for appId={}: {}",
                                                                                wabaAppId,
                                                                                errorBody);
                                                                return Mono.error(new RuntimeException(
                                                                                "Facebook API returned 5xx: "
                                                                                                + errorBody));
                                                        }))
                                        .bodyToMono(UploadSessionResponse.class)
                                        .block();

                        log.info("Upload session initiated successfully. Session ID: {}",
                                        response.getUploadSessionId());
                        return FacebookApiResponse.success(response, 200);

                } catch (WebClientResponseException ex) {
                        log.error("Upload session initiation failed. AppId={} Status={} Response={}",
                                        wabaAppId, ex.getStatusCode().value(), ex.getResponseBodyAsString());
                        return FacebookApiResponse.error(ex.getResponseBodyAsString(), ex.getStatusCode().value());

                } catch (Exception ex) {
                        log.error("Unexpected error initiating upload session for AppId={}", wabaAppId, ex);
                        return FacebookApiResponse.error("Internal Server Error: " + ex.getMessage(), 500);
                }
        }

        /**
         * Step 2: Uploads media to Facebook using an upload session ID.
         * Wrapped with Retry, CircuitBreaker, and RateLimiter for resilience.
         */
        public FacebookApiResponse<UploadMediaResponse> uploadResumableMediaToFacebook(
                        String sessionId,
                        File file,
                        String accessToken,
                        String offset) throws IOException {

                if (!file.exists()) {
                        return FacebookApiResponse.error("File not found: " + file.getAbsolutePath(), 400);
                }

                URI uri = URI.create(properties.getBaseUrl() + "/" + properties.getApiVersion() + "/" + sessionId);
                log.info("Uploading media chunk to Facebook: {}", uri);

                try {
                        FileSystemResource fileResource = new FileSystemResource(file);

                        UploadMediaResponse response = webClientBuilder.build()
                                        .post()
                                        .uri(uri)
                                        .header(HttpHeaders.AUTHORIZATION, "OAuth " + accessToken.trim())
                                        .header("file_offset", String.valueOf(offset).trim())
                                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                                        .body(BodyInserters.fromResource(fileResource))
                                        .retrieve()
                                        .onStatus(HttpStatusCode::is4xxClientError, r -> r.bodyToMono(String.class)
                                                        .flatMap(errorBody -> {
                                                                log.error("Facebook API 4xx during media upload for sessionId={}: {}",
                                                                                sessionId,
                                                                                errorBody);
                                                                return Mono.error(new RuntimeException(
                                                                                "Facebook API returned 4xx: "
                                                                                                + errorBody));
                                                        }))
                                        .onStatus(HttpStatusCode::is5xxServerError, r -> r.bodyToMono(String.class)
                                                        .flatMap(errorBody -> {
                                                                log.error("Facebook API 5xx during media upload for sessionId={}: {}",
                                                                                sessionId,
                                                                                errorBody);
                                                                return Mono.error(new RuntimeException(
                                                                                "Facebook API returned 5xx: "
                                                                                                + errorBody));
                                                        }))
                                        .bodyToMono(UploadMediaResponse.class)
                                        .block();

                        if (response == null || response.getFacebookImageUrl() == null) {
                                throw new IllegalStateException("Upload failed or handle not returned");
                        }

                        log.info("Media uploaded successfully. Handle={}", response.getFacebookImageUrl());
                        return FacebookApiResponse.success(response, 200);

                } catch (WebClientResponseException ex) {
                        log.error("Failed to upload media. SessionId={} Status={} Response={}",
                                        sessionId, ex.getStatusCode().value(), ex.getResponseBodyAsString());
                        return FacebookApiResponse.error(ex.getResponseBodyAsString(), ex.getStatusCode().value());

                } catch (Exception ex) {
                        log.error("Unexpected error during media upload. SessionId={}", sessionId, ex);
                        return FacebookApiResponse.error("Internal Server Error: " + ex.getMessage(), 500);
                }
        }

        /**
         * Step 3: Retrieves current file offset for an ongoing upload session.
         */
        /**
         * Gets the current file offset for an ongoing Facebook upload session.
         * Used to resume chunked uploads or verify completion.
         *
         * @param sessionId   The upload session ID (e.g., "upload:123456")
         * @param accessToken Valid user access token with upload permission
         * @param apiVersion  Graph API version (e.g., "v23.0")
         * @return UploadOffsetResponse with current file offset
         */
        public UploadOffsetResponse getUploadOffset(String sessionId,
                        String accessToken) {

                URI uri = UriComponentsBuilder
                                .fromUriString(properties.getBaseUrl())
                                .pathSegment(properties.getApiVersion(), sessionId)
                                .queryParam("access_token", accessToken)
                                .build()
                                .toUri();

                log.info("Checking upload offset: {}", uri);

                return webClientBuilder.build()
                                .get()
                                .uri(uri)
                                .header(HttpHeaders.AUTHORIZATION, "OAuth " + accessToken)
                                .accept(MediaType.APPLICATION_JSON)
                                .retrieve()
                                .bodyToMono(UploadOffsetResponse.class)
                                .doOnNext(resp -> log.info("Received file_offset: {}", resp.getFileOffset()))
                                .block();
        }

        /**
         * Fallback method for getTemplateByName when Retry or CircuitBreaker triggers.
         */
        @SuppressWarnings("unused")
        private FacebookApiResponse<JsonNode> getTemplateByNameFallback(
                        String templateName, String wabaId, String accessToken, Throwable ex) {

                log.error("Fallback triggered while fetching template by name. Name={} WABA_ID={}", templateName,
                                wabaId, ex);
                return FacebookApiResponse.error("Fallback triggered: " + ex.getMessage(), 500);
        }

        /**
         * Fallback method triggered by Retry or CircuitBreaker.
         */
        @SuppressWarnings("unused")
        private FacebookApiResponse<JsonNode> createTemplateFallback(String bodyJson, String wabaId,
                        String accessToken, Throwable ex) {
                log.error("Fallback triggered while sending template to Facebook. WABA_ID={}", wabaId, ex);
                return FacebookApiResponse.error("Fallback triggered: " + ex.getMessage(), 500);
        }

        /**
         * Fallback method for getAllTemplates when Retry or CircuitBreaker triggers.
         */
        @SuppressWarnings("unused")
        private FacebookApiResponse<JsonNode> getAllTemplatesFallback(
                        String wabaId,
                        String accessToken,
                        Optional<String> status,
                        Optional<String> language,
                        Optional<String> category,
                        Optional<String> name,
                        Optional<Integer> limit,
                        Throwable ex) {

                log.error("Fallback triggered while fetching all templates. WABA_ID={}", wabaId, ex);
                return FacebookApiResponse.error("Fallback triggered: " + ex.getMessage(), 500);
        }
}
