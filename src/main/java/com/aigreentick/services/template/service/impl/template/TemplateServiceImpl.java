package com.aigreentick.services.template.service.impl.template;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.aigreentick.services.template.constants.TemplateConstants;
import com.aigreentick.services.template.dto.response.template.TemplateResponseDto;
import com.aigreentick.services.template.enums.TemplateStatus;
import com.aigreentick.services.template.mapper.TemplateMapper;
import com.aigreentick.services.template.model.template.Template;
import com.aigreentick.services.template.repository.template.TemplateRepository;

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

    public static final String STATUS_NEW_CREATED = "new_created";

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

    // ============== NEW METHODS FOR SYNC OPTIMIZATION ==============

    /**
     * Find all templates with status 'new_created' for a user
     */
    public List<Template> findNewCreatedTemplates(Long userId) {
        log.debug("Fetching new_created templates for userId: {}", userId);
        return templateRepository.findByUserIdAndStatusAndDeletedAtIsNull(userId, STATUS_NEW_CREATED);
    }

    /**
     * Find new_created templates by names for matching with Facebook approved templates
     * Returns a Map of templateName -> Template for quick lookup
     */
    public Map<String, Template> findNewCreatedTemplatesByNames(Long userId, Set<String> names) {
        log.debug("Fetching new_created templates by names for userId: {}, names count: {}", userId, names.size());
        
        List<Template> templates = templateRepository.findByUserIdAndStatusAndNameIn(
                userId, STATUS_NEW_CREATED, names);
        
        return templates.stream()
                .collect(Collectors.toMap(
                        Template::getName,
                        Function.identity(),
                        (existing, replacement) -> existing // Keep first if duplicates
                ));
    }

    /**
     * Find template by name and status
     */
    public Optional<Template> findByNameAndStatus(Long userId, String name, String status) {
        log.debug("Fetching template by name: {} and status: {} for userId: {}", name, status, userId);
        return templateRepository.findByUserIdAndNameAndStatusAndDeletedAtIsNull(userId, name, status);
    }

    /**
     * Get all template names with status 'new_created' for a user
     */
    public Set<String> findNewCreatedTemplateNames(Long userId) {
        log.debug("Fetching new_created template names for userId: {}", userId);
        return templateRepository.findNamesByUserIdAndStatus(userId, STATUS_NEW_CREATED);
    }

    /**
     * Update template with components and texts from Facebook sync
     * This is used when a new_created template gets approved on Facebook
     */
    @Transactional
    public Template updateTemplateFromFacebookSync(Template existingTemplate, Template syncedData) {
        log.info("Updating template {} from Facebook sync", existingTemplate.getName());

        // Update basic fields from Facebook
        existingTemplate.setWaId(syncedData.getWaId());
        existingTemplate.setStatus(syncedData.getStatus());
        existingTemplate.setCategory(syncedData.getCategory());
        existingTemplate.setPreviousCategory(syncedData.getPreviousCategory());
        existingTemplate.setTemplateType(syncedData.getTemplateType());
        existingTemplate.setUpdatedAt(LocalDateTime.now());

        // Clear existing components and texts (they will be replaced)
        existingTemplate.getComponents().clear();
        existingTemplate.getTexts().clear();

        // Add new components
        if (syncedData.getComponents() != null) {
            for (var component : syncedData.getComponents()) {
                existingTemplate.addComponent(component);
            }
        }

        // Add new texts
        if (syncedData.getTexts() != null) {
            for (var text : syncedData.getTexts()) {
                existingTemplate.addText(text);
            }
        }

        return templateRepository.save(existingTemplate);
    }

    // ============== EXISTING METHODS ==============

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
        log.info("Deleting all templates for userId: {}", userId);

        // Fetch templates (this loads them into persistence context)
        List<Template> templates = templateRepository.findByUserIdAndDeletedAtIsNull(userId);

        if (!templates.isEmpty()) {
            // deleteAll triggers CascadeType.ALL on children
            templateRepository.deleteAll(templates);
            log.info("Deleted {} templates for userId: {}", templates.size(), userId);
        }
    }
}