package com.aigreentick.services.template.mapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.aigreentick.services.template.dto.build.SupportedAppDto;
import com.aigreentick.services.template.dto.build.TemplateCarouselButton;
import com.aigreentick.services.template.dto.build.TemplateCarouselCardComponent;
import com.aigreentick.services.template.dto.build.TemplateCarouselExample;
import com.aigreentick.services.template.dto.build.TemplateComponentButtonDto;
import com.aigreentick.services.template.dto.build.TemplateComponentCardsDto;
import com.aigreentick.services.template.dto.build.TemplateComponentDto;
import com.aigreentick.services.template.dto.build.TemplateDto;
import com.aigreentick.services.template.dto.build.TemplateExampleDto;
import com.aigreentick.services.template.dto.build.TemplateTextDto;
import com.aigreentick.services.template.dto.request.CreateTemplateResponseDto;
import com.aigreentick.services.template.dto.request.SupportedAppRequest;
import com.aigreentick.services.template.dto.request.TemplateCarouselButtonRequest;
import com.aigreentick.services.template.dto.request.TemplateCarouselCardComponentRequest;
import com.aigreentick.services.template.dto.request.TemplateCarouselExampleRequest;
import com.aigreentick.services.template.dto.request.TemplateComponentButtonRequest;
import com.aigreentick.services.template.dto.request.TemplateComponentCardsRequest;
import com.aigreentick.services.template.dto.request.TemplateComponentRequest;
import com.aigreentick.services.template.dto.request.TemplateExampleRequest;
import com.aigreentick.services.template.dto.request.TemplateRequest;
import com.aigreentick.services.template.dto.request.TemplateTextRequest;
import com.aigreentick.services.template.dto.response.TemplateResponseDto;
import com.aigreentick.services.template.enums.ComponentType;
import com.aigreentick.services.template.enums.MediaFormat;
import com.aigreentick.services.template.model.SupportedApp;
import com.aigreentick.services.template.model.Template;
import com.aigreentick.services.template.model.TemplateCarouselCard;
import com.aigreentick.services.template.model.TemplateCarouselCardButton;
import com.aigreentick.services.template.model.TemplateComponent;
import com.aigreentick.services.template.model.TemplateComponentButton;
import com.aigreentick.services.template.model.TemplateText;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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

    // ==================== PUBLIC API - FACEBOOK TO ENTITY ====================

    public Template fromFacebookTemplate(TemplateRequest fbTemplate, Long userId, String wabaId) {
        Template template = buildBaseTemplate(fbTemplate, userId);

        if (fbTemplate.getComponents() != null) {
            for (TemplateComponentRequest compReq : fbTemplate.getComponents()) {
                template.addComponent(mapToComponentEntity(compReq));
            }

            // Extract text variables with example values
            extractAllTextVariables(fbTemplate.getComponents(), template);
        }

        return template;
    }

    // ==================== PUBLIC API - REQUEST TO ENTITY ====================

    public Template toTemplateEntity(CreateTemplateResponseDto request, Long userId) {
        TemplateRequest req = request.getTemplate();
        Template template = buildBaseTemplate(req, userId);

        if (req.getComponents() != null) {
            for (TemplateComponentRequest compReq : req.getComponents()) {
                template.addComponent(mapToComponentEntity(compReq));
            }

            extractAllTextVariables(req.getComponents(), template);
        }

        if (request.getVariables() != null) {
            for (TemplateTextRequest textReq : request.getVariables()) {
                template.addText(mapToTextEntity(textReq));
            }
        }

        return template;
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

    // ==================== TEXT VARIABLE EXTRACTION ====================

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
     * Extract HEADER text variables with example values
     */
    private void extractHeaderTextVariables(TemplateExampleRequest example, Template template) {
        if (example == null || example.getHeaderText() == null) return;

        List<String> headerTexts = example.getHeaderText();
        for (int i = 0; i < headerTexts.size(); i++) {
            String exampleValue = headerTexts.get(i);
            template.addText(buildTemplateText(
                    "HEADER",
                    exampleValue,  // Store example value in text field
                    i,
                    false,
                    null,
                    null  // defaultValue is null, user configures later
            ));
        }
        log.debug("Extracted {} HEADER text variables with examples", headerTexts.size());
    }

    /**
     * Extract BODY text variables with example values
     */
    private void extractBodyTextVariables(TemplateExampleRequest example, Template template) {
        if (example == null || example.getBodyText() == null || example.getBodyText().isEmpty()) return;

        List<String> bodyTexts = example.getBodyText().get(0);
        for (int i = 0; i < bodyTexts.size(); i++) {
            String exampleValue = bodyTexts.get(i);
            template.addText(buildTemplateText(
                    "BODY",
                    exampleValue,  // Store example value in text field
                    i,
                    false,
                    null,
                    null
            ));
        }
        log.debug("Extracted {} BODY text variables with examples", bodyTexts.size());
    }

    /**
     * Extract BUTTON text variables with example values
     */
    private void extractButtonTextVariables(List<TemplateComponentButtonRequest> buttons, Template template) {
        if (buttons == null) return;

        for (TemplateComponentButtonRequest btn : buttons) {
            if (btn.getExample() == null || btn.getExample().isEmpty()) continue;

            int buttonIndex = btn.getIndex() != null ? btn.getIndex() : 0;
            List<String> examples = btn.getExample();
            
            for (int i = 0; i < examples.size(); i++) {
                String exampleValue = examples.get(i);
                template.addText(buildTemplateText(
                        "BUTTON",
                        exampleValue,  // Store example value in text field
                        i,
                        false,
                        null,
                        null
                ));
            }
            log.debug("Extracted {} BUTTON text variables for button index {} with examples",
                    examples.size(), buttonIndex);
        }
    }

    /**
     * Extract CAROUSEL text variables with cardIndex and example values
     * Uses loop index as cardIndex since Facebook API may not return explicit index
     */
    private void extractCarouselTextVariables(TemplateComponentRequest component, Template template) {
        if (component.getCards() == null) return;

        List<TemplateComponentCardsRequest> cards = component.getCards();
        
        for (int i = 0; i < cards.size(); i++) {
            TemplateComponentCardsRequest card = cards.get(i);
            
            // Use explicit index if available, otherwise use loop index
            int cardIndex = card.getIndex() != null ? card.getIndex() : i;

            if (card.getComponents() == null) continue;

            log.debug("Processing carousel card at index: {}", cardIndex);

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
     * Extract carousel HEADER variables with cardIndex and example values
     */
    private void extractCarouselHeaderTexts(TemplateCarouselCardComponentRequest cardComp,
                                            int cardIndex, Template template) {
        if (cardComp.getExample() == null) return;

        TemplateCarouselExampleRequest example = cardComp.getExample();
        if (example.getHeaderText() != null && !example.getHeaderText().isEmpty()) {
            List<String> headerTexts = example.getHeaderText();
            for (int i = 0; i < headerTexts.size(); i++) {
                String exampleValue = headerTexts.get(i);
                template.addText(buildTemplateText(
                        "HEADER",
                        exampleValue,  // Example value
                        i,
                        true,
                        cardIndex,     // Card index for carousel
                        null
                ));
            }
            log.debug("Extracted {} HEADER variables for card {} with examples", 
                    headerTexts.size(), cardIndex);
        }
    }

    /**
     * Extract carousel BODY variables with cardIndex and example values
     */
    private void extractCarouselBodyTexts(TemplateCarouselCardComponentRequest cardComp,
                                          int cardIndex, Template template) {
        if (cardComp.getExample() == null) return;

        TemplateCarouselExampleRequest example = cardComp.getExample();
        if (example.getBodyText() != null && !example.getBodyText().isEmpty()) {
            List<String> bodyTexts = example.getBodyText().get(0);
            for (int i = 0; i < bodyTexts.size(); i++) {
                String exampleValue = bodyTexts.get(i);
                template.addText(buildTemplateText(
                        "BODY",
                        exampleValue,  // Example value
                        i,
                        true,
                        cardIndex,     // Card index for carousel
                        null
                ));
            }
            log.debug("Extracted {} BODY variables for card {} with examples", 
                    bodyTexts.size(), cardIndex);
        }
    }

    /**
     * Extract carousel BUTTON variables with cardIndex and example values
     */
    private void extractCarouselButtonTexts(TemplateCarouselCardComponentRequest cardComp,
                                            int cardIndex, Template template) {
        if (cardComp.getButtons() == null) return;

        for (TemplateCarouselButtonRequest btn : cardComp.getButtons()) {
            if (btn.getExample() == null || btn.getExample().isEmpty()) continue;

            int buttonIndex = btn.getIndex() != null ? btn.getIndex() : 0;
            List<String> examples = btn.getExample();
            
            for (int i = 0; i < examples.size(); i++) {
                String exampleValue = examples.get(i);
                template.addText(buildTemplateText(
                        "BUTTON",
                        exampleValue,  // Example value
                        buttonIndex,   // Button index within card
                        true,
                        cardIndex,     // Card index for carousel
                        null
                ));
            }
            log.debug("Extracted {} BUTTON variables for card {} button {} with examples",
                    examples.size(), cardIndex, buttonIndex);
        }
    }

    // ==================== ENTITY BUILDERS ====================

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
     * Build TemplateText with:
     * - text: stores example value from Facebook (used as fallback)
     * - textIndex: position of variable within component type
     * - cardIndex: card position for carousel templates
     * - defaultValue: user-configured default (null initially)
     */
    private TemplateText buildTemplateText(String type, String exampleValue, int textIndex,
                                           boolean isCarousel, Integer cardIndex, String defaultValue) {
        return TemplateText.builder()
                .type(type)
                .text(exampleValue)      // Example value as fallback
                .textIndex(textIndex)
                .isCarousel(isCarousel)
                .cardIndex(cardIndex)
                .defaultValue(defaultValue)  // User configures this later
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ==================== REQUEST TO ENTITY MAPPING ====================

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

        if (req.getButtons() != null) {
            AtomicInteger btnIndex = new AtomicInteger(0);
            for (TemplateComponentButtonRequest btnReq : req.getButtons()) {
                int index = btnReq.getIndex() != null ? btnReq.getIndex() : btnIndex.getAndIncrement();
                comp.addButton(mapToButtonEntity(btnReq, index));
            }
        }

        if (req.getCards() != null) {
            List<TemplateComponentCardsRequest> cards = req.getCards();
            for (int i = 0; i < cards.size(); i++) {
                TemplateComponentCardsRequest cardReq = cards.get(i);
                // Use explicit index if available, otherwise use loop index
                int index = cardReq.getIndex() != null ? cardReq.getIndex() : i;
                comp.addCarouselCard(mapToCarouselCardEntity(cardReq, index));
            }
        }

        return comp;
    }

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

        if (req.getSupportedApps() != null) {
            for (SupportedAppRequest appReq : req.getSupportedApps()) {
                btn.addSupportedApp(mapToSupportedAppEntity(appReq));
            }
        }

        return btn;
    }

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

        if (!allParameters.isEmpty()) {
            card.setParameters(serializeToJson(allParameters));
        }

        return card;
    }

    private void processCarouselCardComponent(TemplateCarouselCardComponentRequest compReq,
                                              TemplateCarouselCard card, List<String> allParameters) {
        String type = compReq.getType().toUpperCase();

        switch (type) {
            case "HEADER" -> {
                card.setMediaType(compReq.getFormat() != null ? compReq.getFormat().getValue() : null);
                card.setHeader(compReq.getText());

                if (compReq.getExample() != null) {
                    if (compReq.getExample().getHeaderHandle() != null
                            && !compReq.getExample().getHeaderHandle().isEmpty()) {
                        card.setImageUrl(compReq.getExample().getHeaderHandle().get(0));
                    }
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

    private TemplateText mapToTextEntity(TemplateTextRequest req) {
        return TemplateText.builder()
                .type(req.getType())
                .text(req.getText())
                .isCarousel(req.isCarousel())
                .textIndex(req.getTextIndex())
                .cardIndex(req.getCardIndex())
                .defaultValue(null)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private SupportedApp mapToSupportedAppEntity(SupportedAppRequest req) {
        return SupportedApp.builder()
                .packageName(req.getPackageName())
                .signatureHash(req.getSignatureHash())
                .build();
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
                .example(button.getExample())
                .supportedApps(button.getSupportedApps() != null
                        ? button.getSupportedApps().stream().map(this::mapToSupportedAppDto).collect(Collectors.toList())
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
        dto.setText(text.getText());           // Example value (fallback)
        dto.setDefaultValue(text.getDefaultValue());  // User-configured default
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

    private int countVariables(String text) {
        if (text == null || text.isEmpty()) return 0;

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{\\{\\d+\\}\\}");
        java.util.regex.Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }
}