package com.aigreentick.services.template.service.impl;

import org.springframework.stereotype.Service;

import com.aigreentick.services.template.constants.TemplateConstants;
import com.aigreentick.services.template.model.Template;
import com.aigreentick.services.template.repository.TemplateRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class TemplateServiceImpl {
    private final TemplateRepository templateRepository;

    public void save(Template template) {
        log.info("Saving template: {}", template.getName());
        templateRepository.save(template);
    }

    public void checkDuplicateTemplate(String name, Long userId) {
        log.info("Checking duplicate template - Name: {}, UserId: {}", name, userId);
        
        boolean exists = templateRepository.existsByNameAndUserIdAndDeletedAtIsNull(name, userId);
        
        if (exists) {
            String errorMsg = String.format(TemplateConstants.Messages.TEMPLATE_EXISTS_MSG, name);
            log.error(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }
    }
}