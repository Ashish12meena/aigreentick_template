package com.aigreentick.services.template.service.impl.template;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aigreentick.services.template.dto.request.template.TemplateTextResponseDto;
import com.aigreentick.services.template.dto.request.template.TemplateTextUpdateDefaultRequest.TemplateTextDefaultItems;
import com.aigreentick.services.template.model.template.Template;
import com.aigreentick.services.template.model.template.TemplateText;
import com.aigreentick.services.template.repository.template.TemplateRepository;
import com.aigreentick.services.template.repository.template.TemplateTextRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateTextServiceImpl {

    private final TemplateRepository templateRepository;
    private final TemplateTextRepository templateTextRepository;

    /**
     * Get all variables for a template
     */
    @Transactional(readOnly = true)
    public List<TemplateTextResponseDto> getTemplateVariables(Long templateId, Long userId) {
        log.info("Fetching variables for templateId: {}, userId: {}", templateId, userId);

        Template template = getTemplateAndValidateOwnership(templateId, userId);

        return template.getTexts().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Update default values for multiple variables
     */
    @Transactional
    public List<TemplateTextResponseDto> updateVariableDefaults(
            Long templateId,
            Long userId,
            List<TemplateTextDefaultItems> defaults) {

        log.info("Updating {} default values for templateId: {}", defaults.size(), templateId);

        Template template = getTemplateAndValidateOwnership(templateId, userId);

        // Create a map for quick lookup of existing variables
        Map<Long, TemplateText> textById = template.getTexts().stream()
                .collect(Collectors.toMap(TemplateText::getId, Function.identity()));

        // Also create a composite key map for matching by type+index+cardIndex
        Map<String, TemplateText> textByCompositeKey = template.getTexts().stream()
                .collect(Collectors.toMap(
                        this::createCompositeKey,
                        Function.identity(),
                        (existing, replacement) -> existing
                ));

        for (TemplateTextDefaultItems item : defaults) {
            TemplateText textToUpdate = null;

            // First try to find by ID
            if (item.getId() != null) {
                textToUpdate = textById.get(item.getId());
            }

            // If not found by ID, try by composite key (type + textIndex + cardIndex + isCarousel)
            if (textToUpdate == null && item.getType() != null && item.getTextIndex() != null) {
                String compositeKey = createCompositeKey(item);
                textToUpdate = textByCompositeKey.get(compositeKey);
            }

            if (textToUpdate != null) {
                textToUpdate.setDefaultValue(item.getDefaultValue());
                textToUpdate.setUpdatedAt(LocalDateTime.now());
                log.debug("Updated default value for variable id: {}, type: {}, textIndex: {}",
                        textToUpdate.getId(), textToUpdate.getType(), textToUpdate.getTextIndex());
            } else {
                log.warn("Variable not found for update: id={}, type={}, textIndex={}, cardIndex={}",
                        item.getId(), item.getType(), item.getTextIndex(), item.getCardIndex());
            }
        }

        // Save all changes
        templateTextRepository.saveAll(template.getTexts());

        log.info("Successfully updated default values for templateId: {}", templateId);

        return template.getTexts().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Update a single variable's default value
     */
    @Transactional
    public TemplateTextResponseDto updateSingleVariableDefault(
            Long templateId,
            Long variableId,
            Long userId,
            String defaultValue) {

        log.info("Updating single variable default: templateId={}, variableId={}", templateId, variableId);

        // Validate template ownership
        getTemplateAndValidateOwnership(templateId, userId);

        TemplateText text = templateTextRepository.findById(variableId)
                .orElseThrow(() -> new IllegalArgumentException("Variable not found with id: " + variableId));

        // Verify the variable belongs to the correct template
        if (!text.getTemplate().getId().equals(templateId)) {
            throw new IllegalArgumentException("Variable does not belong to the specified template");
        }

        text.setDefaultValue(defaultValue);
        text.setUpdatedAt(LocalDateTime.now());

        TemplateText saved = templateTextRepository.save(text);

        log.info("Successfully updated default value for variableId: {}", variableId);

        return mapToDto(saved);
    }

    /**
     * Clear all default values for a template
     */
    @Transactional
    public void clearAllDefaults(Long templateId, Long userId) {
        log.info("Clearing all defaults for templateId: {}", templateId);

        Template template = getTemplateAndValidateOwnership(templateId, userId);

        for (TemplateText text : template.getTexts()) {
            text.setDefaultValue(null);
            text.setUpdatedAt(LocalDateTime.now());
        }

        templateTextRepository.saveAll(template.getTexts());

        log.info("Successfully cleared all defaults for templateId: {}", templateId);
    }

    /**
     * Get template and validate that it belongs to the user
     */
    private Template getTemplateAndValidateOwnership(Long templateId, Long userId) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found with id: " + templateId));

        if (!template.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Template does not belong to the user");
        }

        if (template.getDeletedAt() != null) {
            throw new IllegalArgumentException("Template has been deleted");
        }

        return template;
    }

    /**
     * Create composite key for matching variables
     */
    private String createCompositeKey(TemplateText text) {
        return String.format("%s_%d_%d_%b",
                text.getType(),
                text.getTextIndex(),
                text.getCardIndex() != null ? text.getCardIndex() : -1,
                text.getIsCarousel() != null ? text.getIsCarousel() : false);
    }

    /**
     * Create composite key from request item
     */
    private String createCompositeKey(TemplateTextDefaultItems item) {
        return String.format("%s_%d_%d_%b",
                item.getType(),
                item.getTextIndex(),
                item.getCardIndex() != null ? item.getCardIndex() : -1,
                item.getIsCarousel() != null ? item.getIsCarousel() : false);
    }

    /**
     * Map entity to DTO
     */
    private TemplateTextResponseDto mapToDto(TemplateText text) {
        return TemplateTextResponseDto.builder()
                .id(text.getId())
                .templateId(text.getTemplate().getId())
                .type(text.getType())
                .text(text.getText())
                .textIndex(text.getTextIndex())
                .defaultValue(text.getDefaultValue())
                .isCarousel(text.getIsCarousel())
                .cardIndex(text.getCardIndex())
                .build();
    }
}