package com.aigreentick.services.template.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.aigreentick.services.template.constants.TemplateConstants;
import com.aigreentick.services.template.dto.response.TemplateResponseDto;
import com.aigreentick.services.template.enums.TemplateStatus;
import com.aigreentick.services.template.mapper.TemplateMapper;
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
    private final TemplateMapper templateMapper;

    @Value("${template.default-page-size:10}")
    private int defaultPageSize;

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

    public Template getTemplateById(Long id) {
        log.debug("Fetching template by ID: {}", id);
        return templateRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Template not found with ID: {}", id);
                    return new IllegalArgumentException(
                            String.format(TemplateConstants.Messages.TEMPLATE_NOT_FOUND, id));
                });
    }

    /**
     * Retrieves paginated templates for a given user with optional filtering.
     */
    public Page<TemplateResponseDto> getTemplatesForUser(
            Long userId,
            String status,
            String search,
            Integer page,
            Integer pageSize) {

        log.info("Fetching templates for userId: {}", userId);

        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }

        // Default pagination
        int currentPage = (page != null && page >= 0) ? page : 0;
        int sizeOfPage = (pageSize != null && pageSize > 0) ? pageSize : defaultPageSize;

        Pageable pageable = PageRequest.of(currentPage, sizeOfPage, Sort.by(Sort.Direction.DESC, "createdAt"));

        // Validate status if provided
        String validatedStatus = validateAndGetStatus(status);

        // Fetch based on filters
        Page<Template> templatePage = fetchTemplates(userId, validatedStatus, search, pageable);

        // Convert to DTOs
        return templatePage.map(templateMapper::mapToTemplateResponse);
    }

    private Page<Template> fetchTemplates(Long userId, String status, String search, Pageable pageable) {
        boolean hasStatus = status != null && !status.isBlank();
        boolean hasSearch = search != null && !search.isBlank();

        if (hasStatus && hasSearch) {
            return templateRepository.findByUserIdAndStatusAndNameContaining(userId, status, search, pageable);
        } else if (hasStatus) {
            return templateRepository.findByUserIdAndStatusAndDeletedAtIsNull(userId, status, pageable);
        } else if (hasSearch) {
            return templateRepository.findByUserIdAndNameContaining(userId, search, pageable);
        } else {
            return templateRepository.findByUserIdAndDeletedAtIsNull(userId, pageable);
        }
    }

    private String validateAndGetStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return TemplateStatus.fromValue(status).getValue();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid status filter provided: {}", status);
            return null;
        }
    }

    public void deleteTemplateById(Long id) {
        templateRepository.deleteById(id);
    }

    @Transactional
    public void deleteAllTemplatesByUserId(Long userId) {
        templateRepository.deleteAllByUserId(userId);
    }
}