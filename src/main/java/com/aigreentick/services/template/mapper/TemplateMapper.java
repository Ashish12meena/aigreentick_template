package com.aigreentick.services.template.mapper;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.aigreentick.services.template.dto.build.*;
import com.aigreentick.services.template.dto.build.TemplateTextDto;
import com.aigreentick.services.template.dto.request.template.create.CreateTemplateRequestDto;
import com.aigreentick.services.template.dto.response.template.TemplateResponseDto;
import com.aigreentick.services.template.enums.MediaFormat;
import com.aigreentick.services.template.model.template.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Maps between Template entities and DTOs.
 * 
 * Responsibilities:
 * - Entity to DTO conversion for API responses
 * - Request DTO to Entity for template creation
 * - Entity to sendable DTO for WhatsApp API
 * 
 * Note: Facebook sync mapping is now in FacebookTemplateSyncMapper
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TemplateMapper {

    private final ObjectMapper objectMapper;

    // ==================== PUBLIC API - RESPONSE MAPPING ====================

    public TemplateResponseDto mapToTemplateResponse(Template template) {
        return TemplateResponseDto.builder()
                .id(template.getId())
                .name(template.getName())
                .status(template.getStatus())
                .category(template.getCategory())
                .language(template.getLanguage())
                .metaTemplateId(template.getWaId())
                .build();
    }

    public TemplateResponseDto mapToTemplateResponse(String templateId, String status, String category) {
        return TemplateResponseDto.builder()
                .metaTemplateId(templateId)
                .status(status)
                .category(category)
                .build();
    }

    // ==================== PUBLIC API - REQUEST TO ENTITY ====================

    /**
     * Maps CreateTemplateRequestDto to Template entity (new_created status).
     * Used when creating templates locally before Facebook submission.
     */
    public Template mapToTemplateEntity(String payload, Long userId, CreateTemplateRequestDto requestDto) {
        return Template.builder()
                .userId(userId)
                .name(requestDto.getTemplate().getName())
                .language(requestDto.getTemplate().getLanguage())
                .category(requestDto.getTemplate().getCategory())
                .status("new_created")
                .payload(payload)
                .build();
    }

    // ==================== PUBLIC API - ENTITY TO DTO (FOR SENDING) ====================

    public TemplateDto toTemplateDto(Template template) {
        TemplateDto dto = new TemplateDto();
        dto.setId(template.getId().toString());
        dto.setName(template.getName());
        dto.setCategory(template.getCategory());
        dto.setLanguage(template.getLanguage());
        dto.setStatus(template.getStatus());
        dto.setMetaTemplateId(template.getWaId());

        if (template.getComponents() != null && !template.getComponents().isEmpty()) {
            dto.setComponents(template.getComponents().stream()
                    .map(this::mapToComponentDto)
                    .collect(Collectors.toList()));
        }

        if (template.getTexts() != null && !template.getTexts().isEmpty()) {
            dto.setTexts(template.getTexts().stream()
                    .map(this::mapToTextDto)
                    .collect(Collectors.toList()));
        }

        return dto;
    }

    // ==================== ENTITY TO DTO MAPPING ====================

    private TemplateComponentDto mapToComponentDto(TemplateComponent component) {
        TemplateComponentDto.TemplateComponentDtoBuilder builder = TemplateComponentDto.builder()
                .type(component.getType())
                .format(component.getFormat())
                .text(component.getText())
                .imageUrl(component.getImageUrl())
                .mediaUrl(component.getImageUrl())
                .addSecurityRecommendation(component.getAddSecurityRecommendation())
                .codeExpirationMinutes(component.getCodeExpirationMinutes());

        if (component.getButtons() != null && !component.getButtons().isEmpty()) {
            builder.buttons(component.getButtons().stream()
                    .map(this::mapToButtonDto)
                    .collect(Collectors.toList()));
        }

        if (component.getCarouselCards() != null && !component.getCarouselCards().isEmpty()) {
            builder.cards(component.getCarouselCards().stream()
                    .map(this::mapToCarouselCardDto)
                    .collect(Collectors.toList()));
        }

        builder.example(buildExampleDto(component));

        return builder.build();
    }

    private TemplateComponentButtonDto mapToButtonDto(TemplateComponentButton button) {
        return TemplateComponentButtonDto.builder()
                .type(button.getType())
                .otpType(button.getOtpType() != null ? button.getOtpType().getValue() : null)
                .phoneNumber(button.getNumber())
                .text(button.getText())
                .url(button.getUrl())
                .index(button.getButtonIndex())
                .autofillText(button.getAutofillText())
                .supportedApps(button.getSupportedApps() != null
                        ? button.getSupportedApps().stream().map(this::mapToSupportedAppDto)
                                .collect(Collectors.toList())
                        : null)
                .build();
    }

    private TemplateComponentCardsDto mapToCarouselCardDto(TemplateCarouselCard card) {
        TemplateComponentCardsDto dto = new TemplateComponentCardsDto();
        dto.setIndex(card.getCardIndex());
        dto.setComponents(buildCarouselCardComponentDtos(card));
        return dto;
    }

    private List<TemplateCarouselCardComponent> buildCarouselCardComponentDtos(TemplateCarouselCard card) {
        List<TemplateCarouselCardComponent> components = new ArrayList<>();
        List<String> parameters = parseJsonArray(card.getParameters());
        int paramIndex = 0;

        // Header
        if (card.getMediaType() != null || card.getImageUrl() != null || card.getHeader() != null) {
            TemplateCarouselCardComponent header = new TemplateCarouselCardComponent();
            header.setType("HEADER");
            header.setFormat(card.getMediaType() != null
                    ? MediaFormat.fromValue(card.getMediaType()).toString()
                    : MediaFormat.IMAGE.toString());
            header.setText(card.getHeader());

            TemplateCarouselExample example = new TemplateCarouselExample();
            if (card.getImageUrl() != null) {
                example.setHeaderHandle(List.of(card.getImageUrl()));
            }

            if (card.getHeader() != null) {
                int headerVarCount = countVariables(card.getHeader());
                if (headerVarCount > 0 && !parameters.isEmpty()) {
                    List<String> headerExamples = new ArrayList<>();
                    for (int i = 0; i < headerVarCount && paramIndex < parameters.size(); i++) {
                        headerExamples.add(parameters.get(paramIndex++));
                    }
                    if (!headerExamples.isEmpty()) {
                        example.setHeaderText(headerExamples);
                    }
                }
            }

            header.setExample(example);
            components.add(header);
        }

        // Body
        if (card.getBody() != null) {
            TemplateCarouselCardComponent body = new TemplateCarouselCardComponent();
            body.setType("BODY");
            body.setText(card.getBody());

            int bodyVarCount = countVariables(card.getBody());
            if (bodyVarCount > 0 && paramIndex < parameters.size()) {
                List<String> bodyExamples = new ArrayList<>();
                for (int i = 0; i < bodyVarCount && paramIndex < parameters.size(); i++) {
                    bodyExamples.add(parameters.get(paramIndex++));
                }
                if (!bodyExamples.isEmpty()) {
                    TemplateCarouselExample bodyExample = new TemplateCarouselExample();
                    bodyExample.setBodyText(List.of(bodyExamples));
                    body.setExample(bodyExample);
                }
            }

            components.add(body);
        }

        // Buttons
        if (card.getButtons() != null && !card.getButtons().isEmpty()) {
            TemplateCarouselCardComponent buttons = new TemplateCarouselCardComponent();
            buttons.setType("BUTTONS");
            buttons.setButtons(card.getButtons().stream()
                    .map(this::mapToCarouselButtonDto)
                    .collect(Collectors.toList()));
            components.add(buttons);
        }

        return components;
    }

    private TemplateCarouselButton mapToCarouselButtonDto(TemplateCarouselCardButton button) {
        TemplateCarouselButton dto = new TemplateCarouselButton();
        dto.setType(button.getType());
        dto.setText(button.getText());
        dto.setUrl(button.getUrl());
        dto.setPhoneNumber(button.getPhoneNumber());
        dto.setIndex(button.getCardButtonIndex());
        dto.setExample(parseJsonArray(button.getParameters()));
        return dto;
    }

    private TemplateTextDto mapToTextDto(TemplateText text) {
        TemplateTextDto dto = new TemplateTextDto();
        dto.setType(text.getType());
        dto.setTextIndex(text.getTextIndex());
        dto.setText(text.getText());
        dto.setDefaultValue(text.getDefaultValue());
        dto.setIsCarousel(text.getIsCarousel());
        dto.setCardIndex(text.getCardIndex());
        return dto;
    }

    private SupportedAppDto mapToSupportedAppDto(SupportedApp app) {
        SupportedAppDto dto = new SupportedAppDto();
        dto.setPackageName(app.getPackageName());
        dto.setSignatureHash(app.getSignatureHash());
        return dto;
    }

    private TemplateExampleDto buildExampleDto(TemplateComponent component) {
        TemplateExampleDto example = new TemplateExampleDto();

        if ("HEADER".equalsIgnoreCase(component.getType())) {
            String format = component.getFormat();
            if ("IMAGE".equalsIgnoreCase(format) || "VIDEO".equalsIgnoreCase(format)
                    || "DOCUMENT".equalsIgnoreCase(format)) {
                if (component.getImageUrl() != null) {
                    example.setHeaderHandle(List.of(component.getImageUrl()));
                }
            } else if ("TEXT".equalsIgnoreCase(format) && component.getText() != null) {
                example.setHeaderText(List.of(component.getText()));
            }
        }

        return example;
    }

    // ==================== UTILITY METHODS ====================

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isEmpty())
            return new ArrayList<>();

        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON array: {}", json, e);
            return new ArrayList<>();
        }
    }

    private int countVariables(String text) {
        if (text == null || text.isEmpty())
            return 0;

        Pattern pattern = Pattern.compile("\\{\\{\\d+\\}\\}");
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find())
            count++;
        return count;
    }
}