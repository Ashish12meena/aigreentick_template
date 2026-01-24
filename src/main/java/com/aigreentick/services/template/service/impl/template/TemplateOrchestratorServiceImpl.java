package com.aigreentick.services.template.service.impl.template;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.aigreentick.services.template.client.adapter.UserService;
import com.aigreentick.services.template.client.adapter.WhatsappClientImpl;
import com.aigreentick.services.template.dto.request.template.TemplateRequest;
import com.aigreentick.services.template.dto.request.template.create.CreateTemplateRequestDto;
import com.aigreentick.services.template.dto.response.common.AccessTokenCredentials;
import com.aigreentick.services.template.dto.response.common.FacebookApiResponse;
import com.aigreentick.services.template.dto.response.template.TemplateResponseDto;
import com.aigreentick.services.template.dto.response.template.TemplateSyncStats;
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

        Template template =   templateMapper.maptoTemplateEntity(payload);

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

        return templateMapper.mapToTemplateResponse(templateId,status,category);
    }

    @Transactional
    public TemplateSyncStats syncTemplatesWithFacebook(Long userId) {
        log.info("Syncing templates for userId: {}", userId);

        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }

        // 1. Fetch WABA access token
        AccessTokenCredentials credentials = userService.getWabaAccessToken(userId);

        // 2. Call Facebook API to get all templates
        FacebookApiResponse<JsonNode> response = whatsappClientImpl.getAllTemplates(
                credentials.getWabaId(),
                credentials.getAccessToken(),
                Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(),
                Optional.empty());

        if (!response.isSuccess() || response.getData() == null) {
            throw new IllegalStateException("Failed to fetch templates from Facebook: "
                    + response.getErrorMessage());
        }

        // 3. Convert Facebook API response into DTO list
        List<TemplateRequest> facebookTemplates = convertToTemplateRequestList(response.getData());
        log.info("Fetched {} templates from Facebook", facebookTemplates.size());

        // 4. Extract Facebook template IDs (as strings - this is the 'id' from Meta)
        Set<String> facebookTemplateIds = facebookTemplates.stream()
                .map(TemplateRequest::getMetaTemplateId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        // 5. Fetch existing waId values from DB for this user
        Set<String> existingWaIds = templateServiceImpl.findWaIdsByUserId(userId);
        log.info("Found {} existing templates in DB for userId: {}", existingWaIds.size(), userId);

        // 6. Determine new templates to insert (in FB but not in DB)
        Set<String> newIds = new HashSet<>(facebookTemplateIds);
        newIds.removeAll(existingWaIds);
        log.info("New templates to insert: {}", newIds.size());

        // 7. Determine stale templates to delete (in DB but not in FB)
        Set<String> deleteIds = new HashSet<>(existingWaIds);
        deleteIds.removeAll(facebookTemplateIds);
        log.info("Stale templates to delete: {}", deleteIds.size());

        // 8. Prepare entities for insertion
        List<Template> toInsert = facebookTemplates.stream()
                .filter(dto -> newIds.contains(dto.getMetaTemplateId()))
                .map(dto -> facebookTemplateSyncMapper.fromFacebookTemplate(dto, userId, credentials.getWabaId()))
                .toList();

        // 9. Delete stale templates
        int deletedCount = 0;
        if (!deleteIds.isEmpty()) {
            deletedCount = templateServiceImpl.softDeleteByWaIdInAndUserId(deleteIds, userId);
            log.info("Deleted {} stale templates", deletedCount);
        }

        // 10. Insert new templates
        if (!toInsert.isEmpty()) {
            templateServiceImpl.saveAll(toInsert);
            log.info("Inserted {} new templates", toInsert.size());
        }

        return new TemplateSyncStats(toInsert.size(), deletedCount);
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