package com.aigreentick.services.template.mapper;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.aigreentick.services.template.dto.request.CreateTemplateResponseDto;
import com.aigreentick.services.template.dto.response.TemplateResponseDto;
import com.aigreentick.services.template.model.Template;

@Component
public class TemplateMapper {

    public Template mapToEntity(CreateTemplateResponseDto request) {
        Template template = new Template();
        
        template.setUserId(request.getTemplate().getUserId());
        template.setName(request.getTemplate().getName());
        template.setLanguage(request.getTemplate().getLanguage());
        template.setCategory(request.getTemplate().getCategory());
        template.setPreviousCategory(request.getTemplate().getPreviousCategory());
        template.setStatus(request.getTemplate().getStatus() != null 
            ? request.getTemplate().getStatus().getValue() 
            : "PENDING");
        
        template.setCreatedAt(LocalDateTime.now());
        template.setUpdatedAt(LocalDateTime.now());
        
        return template;
    }

    public TemplateResponseDto mapToTemplateResponse(Template template) {
        return new TemplateResponseDto(
            template.getId().toString(),
            template.getName(),
            template.getStatus(),
            template.getCategory(),
            template.getLanguage(),
            template.getWaId()
        );
    }
}