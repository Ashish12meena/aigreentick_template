package com.aigreentick.services.template.service.impl.template;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.aigreentick.services.template.client.adapter.UserService;
import com.aigreentick.services.template.client.adapter.WhatsappClientImpl;
import com.aigreentick.services.template.dto.request.template.TemplateRequest;
import com.aigreentick.services.template.dto.request.template.create.CreateTemplateRequestDto;
import com.aigreentick.services.template.dto.request.template.create.VariableDefaultsDto;
import com.aigreentick.services.template.dto.response.common.AccessTokenCredentials;
import com.aigreentick.services.template.dto.response.common.FacebookApiResponse;
import com.aigreentick.services.template.dto.response.template.TemplateResponseDto;
import com.aigreentick.services.template.dto.response.template.TemplateSyncStats;
import com.aigreentick.services.template.enums.TemplateStatus;
import com.aigreentick.services.template.mapper.FacebookTemplateSyncMapper;
import com.aigreentick.services.template.mapper.TemplateMapper;
import com.aigreentick.services.template.model.template.Template;
import com.aigreentick.services.template.util.helper.JsonHelper;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    private final FacebookTemplateSyncMapper facebookTemplateSyncMapper;

    public TemplateResponseDto createTemplate(CreateTemplateRequestDto requestDto, Long userId) {
        log.info("Creating template for userId: {}", userId);

        TemplateRequest templateRequest = requestDto.getTemplate();

        // Check for duplicate
        templateServiceImpl.checkDuplicateTemplate(templateRequest.getName(), userId);

        String payload = JsonHelper.serialize(requestDto);

        Template template = templateMapper.mapToTemplateEntity(payload,userId,requestDto);

        templateServiceImpl.save(template);

        // Fetch WABA credentials
        AccessTokenCredentials credentials = userService.getWabaAccessToken(userId);

        // Serialize template request
        String jsonRequest = JsonHelper.serializeWithSnakeCase(templateRequest);

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
            return new TemplateResponseDto("Invalid response from Facebook API", jsonData);
        }

        return templateMapper.mapToTemplateResponse(templateId, status, category);
    }

    @Transactional
    public TemplateSyncStats syncTemplatesWithFacebook(Long userId) {
        log.info("Syncing templates for userId: {}", userId);

        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }

        // 1. Fetch WABA access token
        AccessTokenCredentials credentials = userService.getWabaAccessToken(userId);

        // 2. Call Facebook API to get ONLY APPROVED templates
        FacebookApiResponse<JsonNode> response = whatsappClientImpl.getAllTemplates(
                credentials.getWabaId(),
                credentials.getAccessToken(),
                Optional.of(TemplateStatus.APPROVED.getValue()), // Filter for APPROVED only
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());

        if (!response.isSuccess() || response.getData() == null) {
            throw new IllegalStateException("Failed to fetch templates from Facebook: "
                    + response.getErrorMessage());
        }

        // 3. Convert Facebook API response into DTO list
        List<TemplateRequest> facebookTemplates = convertToTemplateRequestList(response.getData());
        log.info("Fetched {} APPROVED templates from Facebook", facebookTemplates.size());

        // 4. Extract Facebook template names for matching with new_created templates
        Set<String> facebookTemplateNames = facebookTemplates.stream()
                .map(TemplateRequest::getName)
                .filter(name -> name != null)
                .collect(Collectors.toSet());

        // 5. Extract Facebook template IDs (waId) for deletion check
        Set<String> facebookTemplateIds = facebookTemplates.stream()
                .map(TemplateRequest::getMetaTemplateId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        // 6. Fetch existing waId values from DB for this user (all statuses)
        Set<String> existingWaIds = templateServiceImpl.findWaIdsByUserId(userId);
        log.info("Found {} existing templates in DB for userId: {}", existingWaIds.size(), userId);

        // 7. Fetch new_created templates by names for attribute extraction
        Map<String, Template> newCreatedTemplates = templateServiceImpl.findNewCreatedTemplatesByNames(
                userId, facebookTemplateNames);
        log.info("Found {} new_created templates matching Facebook names", newCreatedTemplates.size());

        // 8. Determine new templates to insert (in FB but not in DB by waId)
        Set<String> newIds = new HashSet<>(facebookTemplateIds);
        newIds.removeAll(existingWaIds);
        log.info("New templates to process: {}", newIds.size());

        // 9. Determine stale templates to delete (in DB but not in FB)
        Set<String> deleteIds = new HashSet<>(existingWaIds);
        deleteIds.removeAll(facebookTemplateIds);
        log.info("Stale templates to delete: {}", deleteIds.size());

        // 10. Process templates - either update existing new_created or insert new
        int insertedCount = 0;
        int updatedCount = 0;

        for (TemplateRequest fbTemplate : facebookTemplates) {
            String templateName = fbTemplate.getName();
            String metaTemplateId = fbTemplate.getMetaTemplateId();

            // Skip if already exists with same waId
            if (existingWaIds.contains(metaTemplateId)) {
                log.debug("Template {} already exists with waId {}, skipping", templateName, metaTemplateId);
                continue;
            }

            // Check if we have a new_created template with this name
            Template existingNewCreated = newCreatedTemplates.get(templateName);

            if (existingNewCreated != null) {
                // Case 1: Found new_created template - extract attributes from payload
                log.info("Updating new_created template: {} with Facebook data", templateName);
                
                VariableDefaultsDto variableDefaults = extractVariableDefaultsFromPayload(
                        existingNewCreated.getPayload());

                Template syncedTemplate = facebookTemplateSyncMapper.fromFacebookTemplateWithDefaults(
                        fbTemplate, userId, credentials.getWabaId(), variableDefaults);

                templateServiceImpl.updateTemplateFromFacebookSync(existingNewCreated, syncedTemplate);
                updatedCount++;
                
            } else {
                // Case 2: No new_created template found - insert as new
                log.info("Inserting new template from Facebook: {}", templateName);
                
                Template newTemplate = facebookTemplateSyncMapper.fromFacebookTemplate(
                        fbTemplate, userId, credentials.getWabaId());

                templateServiceImpl.save(newTemplate);
                insertedCount++;
            }
        }

        // 11. Delete stale templates
        int deletedCount = 0;
        if (!deleteIds.isEmpty()) {
            deletedCount = templateServiceImpl.softDeleteByWaIdInAndUserId(deleteIds, userId);
            log.info("Deleted {} stale templates", deletedCount);
        }

        log.info("Sync complete - Inserted: {}, Updated: {}, Deleted: {}", 
                insertedCount, updatedCount, deletedCount);

        return new TemplateSyncStats(insertedCount + updatedCount, deletedCount);
    }

    /**
     * Extracts VariableDefaultsDto from stored payload JSON.
     * Returns null if payload is invalid or doesn't contain variableDefaults.
     */
    private VariableDefaultsDto extractVariableDefaultsFromPayload(String payload) {
        if (payload == null || payload.isBlank()) {
            log.debug("Payload is null or blank, no variableDefaults available");
            return null;
        }

        try {
            ObjectMapper mapper = objectMapper.copy()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            // Parse the full CreateTemplateRequestDto from payload
            CreateTemplateRequestDto requestDto = mapper.readValue(payload, CreateTemplateRequestDto.class);

            if (requestDto != null && requestDto.getVariableDefaults() != null) {
                log.debug("Successfully extracted variableDefaults from payload");
                return requestDto.getVariableDefaults();
            }

            log.debug("No variableDefaults found in payload");
            return null;

        } catch (Exception e) {
            log.warn("Failed to parse variableDefaults from payload: {}", e.getMessage());
            return null;
        }
    }

    private List<TemplateRequest> convertToTemplateRequestList(JsonNode responseNode) {
        JsonNode dataNode = responseNode.get("data");

        if (dataNode == null || !dataNode.isArray()) {
            log.warn("No 'data' array found in Facebook response");
            return List.of();
        }

        try {
            ObjectMapper mapper = objectMapper.copy()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL);

            return mapper.readValue(
                    dataNode.traverse(),
                    new TypeReference<List<TemplateRequest>>() {
                    });
        } catch (IOException e) {
            log.error("Failed to parse Facebook templates response", e);
            throw new RuntimeException("Failed to map Facebook templates", e);
        }
    }
}