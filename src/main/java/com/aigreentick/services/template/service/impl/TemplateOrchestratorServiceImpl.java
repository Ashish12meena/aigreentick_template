package com.aigreentick.services.template.service.impl;

import org.springframework.stereotype.Service;

import com.aigreentick.services.template.client.adapter.UserService;
import com.aigreentick.services.template.client.adapter.WhatsappClientImpl;
import com.aigreentick.services.template.dto.request.CreateTemplateResponseDto;
import com.aigreentick.services.template.dto.request.TemplateRequest;
import com.aigreentick.services.template.dto.response.AccessTokenCredentials;
import com.aigreentick.services.template.dto.response.TemplateResponseDto;
import com.aigreentick.services.template.mapper.TemplateMapper;
import com.aigreentick.services.template.model.Template;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Service
@Slf4j
@RequiredArgsConstructor
public class TemplateOrchestratorServiceImpl {
    private final TemplateServiceImpl templateServiceImpl;
    private final TemplateMapper templateMapper;
    private final WhatsappClientImpl whatsappClientImpl;
    private final UserService userService;

    public TemplateResponseDto createTemplate(CreateTemplateResponseDto request) {
        log.info("Creating template for userId: {}", request.getTemplate().getUserId());

        TemplateRequest templateRequest = request.getTemplate();
        
        // Check for duplicate
        templateServiceImpl.checkDuplicateTemplate(templateRequest.getName(), templateRequest.getUserId());

        // Fetch WABA credentials
        AccessTokenCredentials credentials = userService.getWabaAccessToken();

        // Serialize template request
        String jsonRequest = serializeTemplate(templateRequest);
        
        // Map and save template
        Template template = templateMapper.mapToEntity(request);
        template.setWaId(credentials.getWabaId());
        template.setPayload(jsonRequest);
        
        templateServiceImpl.save(template);

        return templateMapper.mapToTemplateResponse(template);
    }

    private String serializeTemplate(TemplateRequest request) {
        try {
             ObjectMapper mapper = new ObjectMapper();
             return mapper.writeValueAsString(request);
        } catch (Exception e) {
            log.error("Failed to serialize object to JSON");
            throw new IllegalStateException("JSON serialization failed",e);
        }
    }
}