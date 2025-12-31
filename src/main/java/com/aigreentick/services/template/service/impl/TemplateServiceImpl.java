package com.aigreentick.services.template.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.aigreentick.services.template.constants.TemplateConstants;
import com.aigreentick.services.template.model.Template;
import com.aigreentick.services.template.repository.TemplateRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class TemplateServiceImpl {
    private final TemplateRepository templateRepository;

    @Transactional
    public Template save(Template template) {
        log.info("Saving template: {}", template.getName());
        return templateRepository.save(template);
    }

    @Transactional
    public List<Template> saveAll(List<Template> templates) {
        log.info("Saving {} templates", templates.size());
        return templateRepository.saveAll(templates);
    }

    public void checkDuplicateTemplate(String name, Long userId) {
        log.debug("Checking duplicate template - Name: {}, UserId: {}", name, userId);

        boolean exists = templateRepository.existsByNameAndUserIdAndDeletedAtIsNull(name, userId);

        if (exists) {
            String errorMsg = String.format(TemplateConstants.Messages.TEMPLATE_EXISTS_MSG, name);
            log.error(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }
    }

    /**
     * Find all Facebook template IDs (waId) for a user
     */
    public Set<String> findWaIdsByUserId(Long userId) {
        log.debug("Fetching waIds for userId: {}", userId);
        return templateRepository.findWaIdsByUserId(userId);
    }

    /**
     * Soft delete templates by their Facebook IDs (waId) and userId
     */
    @Transactional
    public int softDeleteByWaIdInAndUserId(Set<String> waIds, Long userId) {
        log.info("Soft deleting {} templates for userId: {}", waIds.size(), userId);
        LocalDateTime now = LocalDateTime.now();
        return templateRepository.softDeleteByWaIdInAndUserId(waIds, userId, now, now);
    }

    /**
     * Hard delete templates by their Facebook IDs (waId) and userId
     */
    @Transactional
    public int deleteByWaIdInAndUserId(Set<String> waIds, Long userId) {
        log.info("Hard deleting {} templates for userId: {}", waIds.size(), userId);
        return templateRepository.deleteByWaIdInAndUserId(waIds, userId);
    }

    /**
     * Find all active templates for a user
     */
    public List<Template> findByUserId(Long userId) {
        log.debug("Fetching templates for userId: {}", userId);
        return templateRepository.findByUserIdAndDeletedAtIsNull(userId);
    }

    /**
     * Check if template exists by Facebook ID
     */
    public boolean existsByWaId(String waId, Long userId) {
        return templateRepository.existsByWaIdAndUserIdAndDeletedAtIsNull(waId, userId);
    }
}