package com.aigreentick.services.template.mapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.aigreentick.services.template.dto.build.*;
import com.aigreentick.services.template.dto.build.TemplateTextDto;
import com.aigreentick.services.template.dto.request.*;
import com.aigreentick.services.template.dto.response.TemplateResponseDto;
import com.aigreentick.services.template.enums.ComponentType;
import com.aigreentick.services.template.enums.MediaFormat;
import com.aigreentick.services.template.model.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * TemplateMapper handles all conversions between:
 * 1. Facebook API responses -> Template Entity (sync flow)
 * 2. Create requests -> Template Entity (create flow)
 * 3. Template Entity -> TemplateDto (send flow)
 * 4. Template Entity -> Response DTOs (API responses)
 * 
 * Text variables are stored in TemplateText for building sendable payloads.
 * Parameters are stored in respective entities for data preservation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TemplateMapper {

    private final ObjectMapper objectMapper;

    // ==================== PUBLIC API - RESPONSE MAPPING ====================

    /**
     * Converts Template entity to API response DTO.
     */
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

    /**
     * Creates response DTO from individual fields.
     */
    public TemplateResponseDto mapToTemplateResponse(String templateId, String status, String category) {
        return TemplateResponseDto.builder()
                .metaTemplateId(templateId)
                .status(status)
                .category(category)
                .build();
    }

    // ==================== PUBLIC API - FACEBOOK TO ENTITY ====================

    /**
     * Maps Facebook API template response to Template entity.
     * Used during sync operations.
     * 
     * Flow: Facebook Response -> TemplateRequest -> Template Entity
     */
    public Template fromFacebookTemplate(TemplateRequest fbTemplate, Long userId, String wabaId) {
        Template template = buildBaseTemplate(fbTemplate, userId);

        if (fbTemplate.getComponents() != null) {
            // Pass 1: Map components to entities
            for (TemplateComponentRequest compReq : fbTemplate.getComponents()) {
                template.addComponent(mapToComponentEntity(compReq));
            }

            // Pass 2: Extract text variables for payload building
            extractAllTextVariables(fbTemplate.getComponents(), template);
        }

        return template;
    }

    // ==================== PUBLIC API - REQUEST TO ENTITY ====================

    /**
     * Maps CreateTemplateResponseDto to Template entity.
     * Used during template creation flow.
     * 
     * Flow: Create Request -> Template Entity
     */
    public Template toTemplateEntity(CreateTemplateResponseDto request, Long userId) {
        TemplateRequest req = request.getTemplate();
        Template template = buildBaseTemplate(req, userId);

        if (req.getComponents() != null) {
            // Pass 1: Map components
            for (TemplateComponentRequest compReq : req.getComponents()) {
                template.addComponent(mapToComponentEntity(compReq));
            }

            // Pass 2: Extract text variables
            extractAllTextVariables(req.getComponents(), template);
        }

        // Pass 3: Add manually provided variables (if any)
        if (request.getVariables() != null) {
            for (TemplateTextRequest textReq : request.getVariables()) {
                template.addText(mapToTextEntity(textReq));
            }
        }

        return template;
    }

    // ==================== PUBLIC API - ENTITY TO DTO (FOR SENDING) ====================

    /**
     * Converts Template entity to TemplateDto for building sendable templates.
     * Used by TemplateBuilderServiceImpl.
     * 
     * Flow: Template Entity -> TemplateDto -> MessageRequest
     */
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

    // ==================== TEXT VARIABLE EXTRACTION ====================

    /**
     * Extracts all text variables from components into TemplateText entries.
     * Handles both regular and carousel components.
     */
    private void extractAllTextVariables(List<TemplateComponentRequest> components, Template template) {
        if (components == null) return;

        for (TemplateComponentRequest component : components) {
            ComponentType type = component.getType();

            if (type == ComponentType.CAROUSEL) {
                extractCarouselTextVariables(component, template);
            } else {
                extractRegularTextVariables(component, template);
            }
        }
    }

    /**
     * Extracts HEADER, BODY, BUTTON text variables from non-carousel components.
     */
    private void extractRegularTextVariables(TemplateComponentRequest component, Template template) {
        ComponentType type = component.getType();
        TemplateExampleRequest example = component.getExample();

        switch (type) {
            case HEADER -> extractHeaderTextVariables(example, template);
            case BODY -> extractBodyTextVariables(example, template);
            case BUTTONS -> extractButtonTextVariables(component.getButtons(), template);
            default -> { /* FOOTER, LIMITED_TIME_OFFER - no variables */ }
        }
    }

    /**
     * Extracts header text variables from example.headerText.
     */
    private void extractHeaderTextVariables(TemplateExampleRequest example, Template template) {
        if (example == null || example.getHeaderText() == null) return;

        List<String> headerTexts = example.getHeaderText();
        for (int i = 0; i < headerTexts.size(); i++) {
            template.addText(buildTemplateText("HEADER", headerTexts.get(i), i, false, null));
        }
        log.debug("Extracted {} HEADER text variables", headerTexts.size());
    }

    /**
     * Extracts body text variables from example.bodyText.
     * Facebook structure: body_text is List<List<String>> - first inner list contains values.
     */
    private void extractBodyTextVariables(TemplateExampleRequest example, Template template) {
        if (example == null || example.getBodyText() == null || example.getBodyText().isEmpty()) return;

        List<String> bodyTexts = example.getBodyText().get(0);
        for (int i = 0; i < bodyTexts.size(); i++) {
            template.addText(buildTemplateText("BODY", bodyTexts.get(i), i, false, null));
        }
        log.debug("Extracted {} BODY text variables", bodyTexts.size());
    }

    /**
     * Extracts button text variables (URL button dynamic suffixes).
     */
    private void extractButtonTextVariables(List<TemplateComponentButtonRequest> buttons, Template template) {
        if (buttons == null) return;

        for (TemplateComponentButtonRequest btn : buttons) {
            if (btn.getExample() == null || btn.getExample().isEmpty()) continue;

            int buttonIndex = btn.getIndex() != null ? btn.getIndex() : 0;
            for (int i = 0; i < btn.getExample().size(); i++) {
                template.addText(buildTemplateText("BUTTON", btn.getExample().get(i), buttonIndex, false, null));
            }
            log.debug("Extracted {} BUTTON text variables for button index {}", btn.getExample().size(), buttonIndex);
        }
    }

    /**
     * Extracts text variables from carousel cards.
     */
    private void extractCarouselTextVariables(TemplateComponentRequest component, Template template) {
        if (component.getCards() == null) return;

        for (TemplateComponentCardsRequest card : component.getCards()) {
            int cardIndex = card.getIndex() != null ? card.getIndex() : 0;

            if (card.getComponents() == null) continue;

            for (TemplateCarouselCardComponentRequest cardComp : card.getComponents()) {
                String compType = cardComp.getType().toUpperCase();

                switch (compType) {
                    case "HEADER" -> extractCarouselHeaderTexts(cardComp, cardIndex, template);
                    case "BODY" -> extractCarouselBodyTexts(cardComp, cardIndex, template);
                    case "BUTTONS" -> extractCarouselButtonTexts(cardComp, cardIndex, template);
                }
            }
        }
    }

    /**
     * Extracts carousel card header text variables.
     */
    private void extractCarouselHeaderTexts(TemplateCarouselCardComponentRequest cardComp, 
                                            int cardIndex, Template template) {
        if (cardComp.getExample() == null) return;

        TemplateCarouselExampleRequest example = cardComp.getExample();
        if (example.getHeaderText() != null && !example.getHeaderText().isEmpty()) {
            for (int i = 0; i < example.getHeaderText().size(); i++) {
                template.addText(buildTemplateText("CAROUSEL_HEADER", example.getHeaderText().get(i), i, true, cardIndex));
            }
        }
    }

    /**
     * Extracts carousel card body text variables.
     */
    private void extractCarouselBodyTexts(TemplateCarouselCardComponentRequest cardComp, 
                                          int cardIndex, Template template) {
        if (cardComp.getExample() == null) return;

        TemplateCarouselExampleRequest example = cardComp.getExample();
        if (example.getBodyText() != null && !example.getBodyText().isEmpty()) {
            List<String> bodyTexts = example.getBodyText().get(0);
            for (int i = 0; i < bodyTexts.size(); i++) {
                template.addText(buildTemplateText("CAROUSEL_BODY", bodyTexts.get(i), i, true, cardIndex));
            }
        }
    }

    /**
     * Extracts carousel card button text variables.
     */
    private void extractCarouselButtonTexts(TemplateCarouselCardComponentRequest cardComp, 
                                            int cardIndex, Template template) {
        if (cardComp.getButtons() == null) return;

        for (TemplateCarouselButtonRequest btn : cardComp.getButtons()) {
            if (btn.getExample() == null || btn.getExample().isEmpty()) continue;

            int buttonIndex = btn.getIndex() != null ? btn.getIndex() : 0;
            for (int i = 0; i < btn.getExample().size(); i++) {
                template.addText(buildTemplateText("BUTTON", btn.getExample().get(i), buttonIndex, true, cardIndex));
            }
        }
    }

    // ==================== ENTITY BUILDERS ====================

    /**
     * Builds base Template entity without components.
     */
    private Template buildBaseTemplate(TemplateRequest req, Long userId) {
        return Template.builder()
                .userId(userId)
                .name(req.getName())
                .language(req.getLanguage())
                .category(req.getCategory())
                .previousCategory(req.getPreviousCategory())
                .status(req.getStatus() != null ? req.getStatus().getValue() : "PENDING")
                .waId(req.getMetaTemplateId())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Builds TemplateText entity.
     */
    private TemplateText buildTemplateText(String type, String text, int textIndex, 
                                           boolean isCarousel, Integer cardIndex) {
        return TemplateText.builder()
                .type(type)
                .text(text)
                .textIndex(textIndex)
                .isCarousel(isCarousel)
                .cardIndex(cardIndex)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ==================== REQUEST TO ENTITY MAPPING ====================

    /**
     * Maps TemplateComponentRequest to TemplateComponent entity.
     */
    private TemplateComponent mapToComponentEntity(TemplateComponentRequest req) {
        TemplateComponent comp = TemplateComponent.builder()
                .type(req.getType() != null ? req.getType().toString() : null)
                .format(req.getFormat())
                .text(req.getText())
                .imageUrl(req.getImageUrl() != null ? req.getImageUrl() : req.getMediaUrl())
                .addSecurityRecommendation(req.getAddSecurityRecommendation())
                .codeExpirationMinutes(req.getCodeExpirationMinutes())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Map buttons with auto-generated indices
        if (req.getButtons() != null) {
            AtomicInteger btnIndex = new AtomicInteger(0);
            for (TemplateComponentButtonRequest btnReq : req.getButtons()) {
                int index = btnReq.getIndex() != null ? btnReq.getIndex() : btnIndex.getAndIncrement();
                comp.addButton(mapToButtonEntity(btnReq, index));
            }
        }

        // Map carousel cards
        if (req.getCards() != null) {
            AtomicInteger cardIndex = new AtomicInteger(0);
            for (TemplateComponentCardsRequest cardReq : req.getCards()) {
                int index = cardReq.getIndex() != null ? cardReq.getIndex() : cardIndex.getAndIncrement();
                comp.addCarouselCard(mapToCarouselCardEntity(cardReq, index));
            }
        }

        return comp;
    }

    /**
     * Maps button request to TemplateComponentButton entity.
     */
    private TemplateComponentButton mapToButtonEntity(TemplateComponentButtonRequest req, int index) {
        TemplateComponentButton btn = TemplateComponentButton.builder()
                .type(req.getType() != null ? req.getType().getValue() : null)
                .otpType(req.getOtpType())
                .number(req.getPhoneNumber())
                .text(req.getText())
                .url(req.getUrl())
                .buttonIndex(index)
                .autofillText(req.getAutofillText())
                .example(req.getExample())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Map supported apps for OTP buttons
        if (req.getSupportedApps() != null) {
            for (SupportedAppRequest appReq : req.getSupportedApps()) {
                btn.addSupportedApp(mapToSupportedAppEntity(appReq));
            }
        }

        return btn;
    }

    /**
     * Maps carousel card request to TemplateCarouselCard entity.
     */
    private TemplateCarouselCard mapToCarouselCardEntity(TemplateComponentCardsRequest req, int index) {
        TemplateCarouselCard card = TemplateCarouselCard.builder()
                .cardIndex(index)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        List<String> allParameters = new ArrayList<>();

        if (req.getComponents() != null) {
            for (TemplateCarouselCardComponentRequest compReq : req.getComponents()) {
                processCarouselCardComponent(compReq, card, allParameters);
            }
        }

        // Store collected parameters as JSON
        if (!allParameters.isEmpty()) {
            card.setParameters(serializeToJson(allParameters));
        }

        return card;
    }

    /**
     * Processes individual carousel card component and populates card entity.
     */
    private void processCarouselCardComponent(TemplateCarouselCardComponentRequest compReq,
                                              TemplateCarouselCard card, List<String> allParameters) {
        String type = compReq.getType().toUpperCase();

        switch (type) {
            case "HEADER" -> {
                card.setMediaType(compReq.getFormat() != null ? compReq.getFormat().getValue() : null);
                card.setHeader(compReq.getText());

                if (compReq.getExample() != null) {
                    // Media URL from headerHandle
                    if (compReq.getExample().getHeaderHandle() != null 
                            && !compReq.getExample().getHeaderHandle().isEmpty()) {
                        card.setImageUrl(compReq.getExample().getHeaderHandle().get(0));
                    }
                    // Header text parameters
                    if (compReq.getExample().getHeaderText() != null) {
                        allParameters.addAll(compReq.getExample().getHeaderText());
                    }
                }
            }
            case "BODY" -> {
                card.setBody(compReq.getText());

                if (compReq.getExample() != null 
                        && compReq.getExample().getBodyText() != null 
                        && !compReq.getExample().getBodyText().isEmpty()) {
                    allParameters.addAll(compReq.getExample().getBodyText().get(0));
                }
            }
            case "BUTTONS" -> {
                if (compReq.getButtons() != null) {
                    for (TemplateCarouselButtonRequest btnReq : compReq.getButtons()) {
                        card.addButton(mapToCarouselButtonEntity(btnReq));
                    }
                }
            }
        }
    }

    /**
     * Maps carousel button request to entity.
     */
    private TemplateCarouselCardButton mapToCarouselButtonEntity(TemplateCarouselButtonRequest req) {
        return TemplateCarouselCardButton.builder()
                .type(req.getType() != null ? req.getType().getValue() : null)
                .text(req.getText())
                .url(req.getUrl())
                .phoneNumber(req.getPhoneNumber())
                .cardButtonIndex(req.getIndex())
                .parameters(serializeToJson(req.getExample()))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Maps TemplateTextRequest to entity.
     */
    private TemplateText mapToTextEntity(TemplateTextRequest req) {
        return TemplateText.builder()
                .type(req.getType())
                .text(req.getText())
                .isCarousel(req.isCarousel())
                .textIndex(req.getTextIndex())
                .cardIndex(req.getCardIndex())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Maps SupportedAppRequest to entity.
     */
    private SupportedApp mapToSupportedAppEntity(SupportedAppRequest req) {
        return SupportedApp.builder()
                .packageName(req.getPackageName())
                .signatureHash(req.getSignatureHash())
                .build();
    }

    // ==================== ENTITY TO DTO MAPPING ====================

    /**
     * Maps TemplateComponent entity to DTO.
     */
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

    /**
     * Maps button entity to DTO.
     */
    private TemplateComponentButtonDto mapToButtonDto(TemplateComponentButton button) {
        return TemplateComponentButtonDto.builder()
                .type(button.getType())
                .otpType(button.getOtpType() != null ? button.getOtpType().getValue() : null)
                .phoneNumber(button.getNumber())
                .text(button.getText())
                .url(button.getUrl())
                .index(button.getButtonIndex())
                .autofillText(button.getAutofillText())
                .example(button.getExample())
                .supportedApps(button.getSupportedApps() != null 
                        ? button.getSupportedApps().stream().map(this::mapToSupportedAppDto).collect(Collectors.toList()) 
                        : null)
                .build();
    }

    /**
     * Maps carousel card entity to DTO.
     */
    private TemplateComponentCardsDto mapToCarouselCardDto(TemplateCarouselCard card) {
        TemplateComponentCardsDto dto = new TemplateComponentCardsDto();
        dto.setIndex(card.getCardIndex());
        dto.setComponents(buildCarouselCardComponentDtos(card));
        return dto;
    }

    /**
     * Builds carousel card component DTOs from entity.
     */
    private List<TemplateCarouselCardComponent> buildCarouselCardComponentDtos(TemplateCarouselCard card) {
        List<TemplateCarouselCardComponent> components = new ArrayList<>();
        List<String> parameters = parseJsonArray(card.getParameters());
        int paramIndex = 0;

        // HEADER component
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

            // Extract header text examples
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

        // BODY component
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

        // BUTTONS component
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

    /**
     * Maps carousel button entity to DTO.
     */
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

    /**
     * Maps TemplateText entity to DTO.
     */
    private TemplateTextDto mapToTextDto(TemplateText text) {
        TemplateTextDto dto = new TemplateTextDto();
        dto.setType(text.getType());
        dto.setTextIndex(text.getTextIndex());
        dto.setText(text.getText());
        return dto;
    }

    /**
     * Maps SupportedApp entity to DTO.
     */
    private SupportedAppDto mapToSupportedAppDto(SupportedApp app) {
        SupportedAppDto dto = new SupportedAppDto();
        dto.setPackageName(app.getPackageName());
        dto.setSignatureHash(app.getSignatureHash());
        return dto;
    }

    /**
     * Builds example DTO for component (header media/text examples).
     */
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

    /**
     * Serializes list to JSON string.
     */
    private String serializeToJson(List<String> list) {
        if (list == null || list.isEmpty()) return null;

        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize list to JSON, using fallback", e);
            return "[" + list.stream()
                    .map(s -> "\"" + s.replace("\"", "\\\"") + "\"")
                    .collect(Collectors.joining(",")) + "]";
        }
    }

    /**
     * Parses JSON array string to List.
     */
    private List<String> parseJsonArray(String json) {
        if (json == null || json.isEmpty()) return new ArrayList<>();

        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON array: {}", json, e);
            return new ArrayList<>();
        }
    }

    /**
     * Counts {{1}}, {{2}} style variables in text.
     */
    private int countVariables(String text) {
        if (text == null || text.isEmpty()) return 0;

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{\\{\\d+\\}\\}");
        java.util.regex.Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }
}