package com.aigreentick.services.template.service.impl;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.aigreentick.services.template.client.adapter.UserService;
import com.aigreentick.services.template.client.adapter.WhatsappClientImpl;
import com.aigreentick.services.template.dto.request.CreateTemplateResponseDto;
import com.aigreentick.services.template.dto.request.TemplateRequest;
import com.aigreentick.services.template.dto.response.AccessTokenCredentials;
import com.aigreentick.services.template.dto.response.FacebookApiResponse;
import com.aigreentick.services.template.dto.response.MetaTemplateIdOnly;
import com.aigreentick.services.template.dto.response.TemplateResponseDto;
import com.aigreentick.services.template.dto.response.TemplateSyncStats;
import com.aigreentick.services.template.mapper.TemplateMapper;
import com.aigreentick.services.template.model.Template;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class TemplateOrchestratorServiceImpl {
    private final TemplateServiceImpl templateServiceImpl;
    private final TemplateMapper templateMapper;
    private final WhatsappClientImpl whatsappClientImpl;
    private final ObjectMapper objectMapper;
    private final UserService userService;

    public TemplateResponseDto createTemplate(CreateTemplateResponseDto request, Long userId) {
        log.info("Creating template for userId: {}", userId);

        TemplateRequest templateRequest = request.getTemplate();

        // Check for duplicate
        templateServiceImpl.checkDuplicateTemplate(templateRequest.getName(), userId);

        // Fetch WABA credentials
        AccessTokenCredentials credentials = userService.getWabaAccessToken();

        // Serialize template request
        String jsonRequest = serializeTemplate(templateRequest);

        // Call WhatsApp API
        FacebookApiResponse<JsonNode> fbResponse = whatsappClientImpl.createTemplate(
                jsonRequest, credentials.getWabaId(), credentials.getAccessToken());

        if (!fbResponse.isSuccess()) {
            return new TemplateResponseDto(fbResponse.getErrorMessage());
        }

        JsonNode jsonData = fbResponse.getData();

        // Handle Facebook-specific errors
        if (jsonData.has("error")) {
            String errorMessage = jsonData.path("error").path("message").asText("Unknown error");
            return new TemplateResponseDto(errorMessage, jsonData);
        }

        String templateId = jsonData.path("id").asText(null);
        String status = jsonData.path("status").asText(null);
        String category = jsonData.path("category").asText(null);

        if (templateId == null || status == null) {
            return new TemplateResponseDto(
                    "Invalid response from Facebook API", jsonData);
        }

        String data = serializeToString(jsonData);

        // Map and save template
        Template template = templateMapper.toTemplateEntity(request, userId);
        template.setWaId(credentials.getWabaId());
        template.setStatus(status);
        template.setPayload(jsonRequest);
        template.setCategory(category);
        template.setResponse(data);

        templateServiceImpl.save(template);

        return templateMapper.mapToTemplateResponse(template);
    }

    private String serializeToString(JsonNode jsonData) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper
                    .setSerializationInclusion(Include.NON_NULL)
                    .writeValueAsString(jsonData);
        } catch (Exception e) {
            log.error("Failed to serialize object to JSON");
            throw new IllegalStateException("JSON serialization failed", e);
        }
    }

    private String serializeTemplate(TemplateRequest request) {
        try {
            ObjectMapper mapper = new ObjectMapper()
                    .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE) // snake_case keys
                    .setSerializationInclusion(Include.NON_NULL); // ignore nulls

            return mapper.writeValueAsString(request);

        } catch (Exception e) {
            log.error("Failed to serialize object to JSON", e);
            throw new IllegalStateException("JSON serialization failed", e);
        }
    }

    @Transactional
    public TemplateSyncStats syncTemplatesWithFacebook(Long userId) {
        log.info("Syncing templates for userId: {}", userId);

        if (userId == null) {
            throw new IllegalArgumentException("Project ID is required");
        }

        // 1. Fetch WABA access token
        AccessTokenCredentials accessTokenCredentials = userService.getWabaAccessToken();

        // 2. Call Facebook API to get all templates
        FacebookApiResponse<JsonNode> response = whatsappClientImpl.getAllTemplates(
                accessTokenCredentials.getWabaId(),
                accessTokenCredentials.getAccessToken(),
                Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(),
                Optional.empty());

        if (response.getStatusCode() != 200 || response.getData() == null) {
            throw new IllegalStateException("Failed to fetch templates from Facebook");
        }

        // Convert Facebook API response into DTO list
        List<TemplateRequest> facebookTemplates = convertToBaseTemplateRequestDtoList(response.getData());

        // 3. Extract Facebook template IDs only
        Set<Long> facebookTemplateIds = facebookTemplates.stream()
                .map(TemplateRequest::getId)
                .collect(Collectors.toSet());

        // 4. Fetch existing template IDs from DB for this project
        List<String> metaTemplateIds = templateServiceImpl.findMetaTemplateIdsByUserId(userId)
                .stream()
                .map(MetaTemplateIdOnly::getMetaTemplateId)
                .toList();

        Set<String> setOfMetaTemplateIds = new HashSet<>(metaTemplateIds);

        // 5. Determine new templates to insert (in FB but not in DB)
        Set<Long> newIds = new HashSet<>(facebookTemplateIds);
        newIds.removeAll(setOfMetaTemplateIds);

        // 6. Determine stale templates to delete (in DB but not in FB)
        Set<Long> deleteIds = new HashSet<>(setOfMetaTemplateIds);
        deleteIds.removeAll(facebookTemplateIds);

        // 7. Prepare entities for insertion
        List<Template> toInsert = facebookTemplates.stream()
                .filter(dto -> newIds.contains(dto.getId()))
                .map(dto -> templateMapper.toTemplateEntity(dto, userId))
                .toList();

        // 8. Delete stale templates
        if (!deleteIds.isEmpty()) {
            templateServiceImpl.deleteByMetaTemplateIdInAndUserId(deleteIds, userId);
        }

        // 9. Insert new templates
        if (!toInsert.isEmpty()) {
            templateServiceImpl.saveAll(toInsert);
        }

        // Return statistics
        return new TemplateSyncStats(toInsert.size(), deleteIds.size());
    }

    private List<TemplateRequest> convertToBaseTemplateRequestDtoList(JsonNode responseNode) {
        JsonNode dataNode = responseNode.get("data");
        List<TemplateRequest> dtos;
        try {
            dtos = objectMapper
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                    .readValue(
                            dataNode.traverse(),
                            new TypeReference<List<BaseTemplateRequestDto>>() {
                            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to map JsonNode list to BaseTemplateRequestDto list", e);
        }
        return dtos;
    }

}