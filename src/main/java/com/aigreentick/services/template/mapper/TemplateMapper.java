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

@Component
@RequiredArgsConstructor
public class TemplateMapper {

    private final ObjectMapper objectMapper;

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
     * Maps a Facebook API template response to a Template entity.
     * Used during sync operations.
     */
    public Template fromFacebookTemplate(TemplateRequest fbTemplate, Long userId, String wabaId) {
        Template template = Template.builder()
                .userId(userId)
                .name(fbTemplate.getName())
                .language(fbTemplate.getLanguage())
                .category(fbTemplate.getCategory())
                .previousCategory(fbTemplate.getPreviousCategory())
                .status(fbTemplate.getStatus() != null ? fbTemplate.getStatus().getValue() : null)
                .waId(fbTemplate.getMetaTemplateId())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        if (fbTemplate.getComponents() != null) {
            for (TemplateComponentRequest compReq : fbTemplate.getComponents()) {
                template.addComponent(toComponent(compReq));
            }

            // SECOND PASS: Extract carousel header and body texts and add to template
            extractCarouselTexts(fbTemplate.getComponents(), template);
        }

        return template;
    }

    /**
     * Maps CreateTemplateResponseDto to Template entity.
     * Used during template creation flow.
     */
    public Template toTemplateEntity(CreateTemplateResponseDto request, Long userId) {
        TemplateRequest req = request.getTemplate();

        Template template = Template.builder()
                .userId(userId)
                .name(req.getName())
                .language(req.getLanguage())
                .category(req.getCategory())
                .previousCategory(req.getPreviousCategory())
                .status(req.getStatus() != null ? req.getStatus().getValue() : "PENDING")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        if (req.getComponents() != null) {
            for (TemplateComponentRequest compReq : req.getComponents()) {
                template.addComponent(toComponent(compReq));
            }

            // SECOND PASS: Extract carousel header and body texts and add to template
            extractCarouselTexts(req.getComponents(), template);
        }

        if (request.getVariables() != null) {
            for (TemplateTextRequest textReq : request.getVariables()) {
                template.addText(toText(textReq));
            }
        }

        return template;
    }

    /**
     * Converts Template entity to TemplateDto for building sendable templates.
     * This is the key method for reusing template building logic.
     */
    public TemplateDto toTemplateDto(Template template) {
        TemplateDto dto = new TemplateDto();
        dto.setId(template.getId().toString());
        dto.setName(template.getName());
        dto.setCategory(template.getCategory());
        dto.setLanguage(template.getLanguage());
        dto.setStatus(template.getStatus());
        dto.setMetaTemplateId(template.getWaId());

        // Map components
        if (template.getComponents() != null && !template.getComponents().isEmpty()) {
            dto.setComponents(template.getComponents().stream()
                    .map(this::toComponentDto)
                    .collect(Collectors.toList()));
        }

        // Map texts/variables
        if (template.getTexts() != null && !template.getTexts().isEmpty()) {
            dto.setTexts(template.getTexts().stream()
                    .map(this::toTextDto)
                    .collect(Collectors.toList()));
        }

        return dto;
    }

    // ==================== SECOND PASS: EXTRACT CAROUSEL TEXTS ====================

    /**
     * Second pass: Extracts header and body text variables from carousel cards
     * and creates TemplateText entries.
     * 
     * Facebook carousel structure has example.body_text for body variables,
     * similar to main body component.
     */
    private void extractCarouselTexts(List<TemplateComponentRequest> components, Template template) {
        if (components == null) {
            return;
        }

        for (TemplateComponentRequest component : components) {
            // Only process CAROUSEL components
            if (component.getType() != ComponentType.CAROUSEL) {
                continue;
            }

            if (component.getCards() == null) {
                continue;
            }

            // Iterate through each card
            for (TemplateComponentCardsRequest cardReq : component.getCards()) {
                int cardIndex = cardReq.getIndex() != null ? cardReq.getIndex() : 0;

                if (cardReq.getComponents() == null) {
                    continue;
                }

                // Process each component within the card
                for (TemplateCarouselCardComponentRequest cardComp : cardReq.getComponents()) {
                    String compType = cardComp.getType().toUpperCase();

                    switch (compType) {
                        case "HEADER" -> extractCarouselHeaderTexts(cardComp, cardIndex, template);
                        case "BODY" -> extractCarouselBodyTexts(cardComp, cardIndex, template);
                    }
                }
            }
        }
    }

    /**
     * Extracts header text variables from carousel card header component.
     * Uses example.headerText from the Facebook API response.
     */
    private void extractCarouselHeaderTexts(TemplateCarouselCardComponentRequest cardComp,
                                            int cardIndex,
                                            Template template) {
        if (cardComp.getExample() == null) {
            return;
        }

        TemplateCarouselExampleRequest example = cardComp.getExample();

        // Extract header text variables from example.headerText
        if (example.getHeaderText() != null && !example.getHeaderText().isEmpty()) {
            List<String> headerTexts = example.getHeaderText();

            for (int textIndex = 0; textIndex < headerTexts.size(); textIndex++) {
                TemplateText templateText = TemplateText.builder()
                        .type("CAROUSEL_HEADER")
                        .text(headerTexts.get(textIndex))
                        .isCarousel(true)
                        .cardIndex(cardIndex)
                        .textIndex(textIndex)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();

                template.addText(templateText);
            }
        }
    }

    /**
     * Extracts body text variables from carousel card body component.
     * Uses example.body_text from the Facebook API response.
     * 
     * Facebook structure:
     * {
     *   "type": "BODY",
     *   "text": "Use code {{1}} to get {{2}} off.",
     *   "example": {
     *     "body_text": [["15OFF", "15%"]]
     *   }
     * }
     */
    private void extractCarouselBodyTexts(TemplateCarouselCardComponentRequest cardComp,
                                          int cardIndex,
                                          Template template) {
        if (cardComp.getExample() == null) {
            return;
        }

        TemplateCarouselExampleRequest example = cardComp.getExample();

        // Extract body text variables from example.bodyText
        // Facebook returns body_text as List<List<String>> - we take the first inner list
        if (example.getBodyText() != null && !example.getBodyText().isEmpty()) {
            List<String> bodyTexts = example.getBodyText().get(0); // First inner list

            for (int textIndex = 0; textIndex < bodyTexts.size(); textIndex++) {
                TemplateText templateText = TemplateText.builder()
                        .type("CAROUSEL_BODY")
                        .text(bodyTexts.get(textIndex))
                        .isCarousel(true)
                        .cardIndex(cardIndex)
                        .textIndex(textIndex)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();

                template.addText(templateText);
            }
        }
    }

    /**
     * Serializes text variables to JSON array string.
     * Example: ["name", "price"] -> "[\"name\",\"price\"]"
     */
    private String serializeToJsonArray(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(texts);
        } catch (JsonProcessingException e) {
            // Fallback: manual serialization
            return "[" + texts.stream()
                    .map(text -> "\"" + text.replace("\"", "\\\"") + "\"")
                    .collect(Collectors.joining(",")) + "]";
        }
    }

    // ==================== ENTITY TO DTO CONVERSION ====================

    private TemplateComponentDto toComponentDto(TemplateComponent component) {
        TemplateComponentDto.TemplateComponentDtoBuilder builder = TemplateComponentDto.builder()
                .type(component.getType())
                .format(component.getFormat())
                .text(component.getText())
                .imageUrl(component.getImageUrl())
                .mediaUrl(component.getImageUrl())
                .addSecurityRecommendation(component.getAddSecurityRecommendation())
                .codeExpirationMinutes(component.getCodeExpirationMinutes());

        // Map buttons
        if (component.getButtons() != null && !component.getButtons().isEmpty()) {
            builder.buttons(component.getButtons().stream()
                    .map(this::toButtonDto)
                    .collect(Collectors.toList()));
        }

        // Map carousel cards
        if (component.getCarouselCards() != null && !component.getCarouselCards().isEmpty()) {
            builder.cards(component.getCarouselCards().stream()
                    .map(this::toCarouselCardDto)
                    .collect(Collectors.toList()));
        }

        // Build example from buttons/cards if needed
        builder.example(buildExampleDto(component));

        return builder.build();
    }

    private TemplateComponentButtonDto toButtonDto(TemplateComponentButton button) {
        return TemplateComponentButtonDto.builder()
                .type(button.getType())
                .otpType(button.getOtpType() != null ? button.getOtpType().getValue() : null)
                .phoneNumber(button.getNumber())
                .text(button.getText())
                .url(button.getUrl())
                .index(button.getButtonIndex())
                .autofillText(button.getAutofillText())
                .example(button.getExample())
                .supportedApps(button.getSupportedApps() != null ? button.getSupportedApps().stream()
                        .map(this::toSupportedApp)
                        .collect(Collectors.toList()) : null)
                .build();
    }

    private TemplateComponentCardsDto toCarouselCardDto(TemplateCarouselCard card) {
        TemplateComponentCardsDto dto = new TemplateComponentCardsDto();
        dto.setIndex(card.getCardIndex());

        // Build components for this card
        List<TemplateCarouselCardComponent> components = buildCarouselCardComponents(card);
        dto.setComponents(components);

        return dto;
    }

    private List<TemplateCarouselCardComponent> buildCarouselCardComponents(TemplateCarouselCard card) {
        List<TemplateCarouselCardComponent> components = new ArrayList<>();

        // Parse parameters JSON to get example values
        List<String> parameterValues = parseParameters(card.getParameters());
        int paramIndex = 0;

        // HEADER component
        if (card.getMediaType() != null || card.getImageUrl() != null || card.getHeader() != null) {
            TemplateCarouselCardComponent header = new TemplateCarouselCardComponent();
            header.setType("HEADER");
            header.setFormat(card.getMediaType() != null ? MediaFormat.fromValue(card.getMediaType()).toString()
                    : MediaFormat.IMAGE.toString());

            // Set actual header text (e.g., "Welcome {{1}}!")
            if (card.getHeader() != null) {
                header.setText(card.getHeader());
            }

            // Build example with media URL and/or header text example values
            TemplateCarouselExample example = new TemplateCarouselExample();
            if (card.getImageUrl() != null) {
                example.setHeaderHandle(List.of(card.getImageUrl()));
            }
            
            // Extract header text example values from parameters
            // Count variables in header text to know how many params belong to header
            if (card.getHeader() != null) {
                int headerVarCount = countVariables(card.getHeader());
                if (headerVarCount > 0 && !parameterValues.isEmpty()) {
                    List<String> headerExamples = new ArrayList<>();
                    for (int i = 0; i < headerVarCount && paramIndex < parameterValues.size(); i++) {
                        headerExamples.add(parameterValues.get(paramIndex++));
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
            body.setText(card.getBody()); // Actual text (e.g., "Use code {{1}} to get {{2}} off.")
            
            // Extract body text example values from remaining parameters
            int bodyVarCount = countVariables(card.getBody());
            if (bodyVarCount > 0 && paramIndex < parameterValues.size()) {
                List<String> bodyExamples = new ArrayList<>();
                for (int i = 0; i < bodyVarCount && paramIndex < parameterValues.size(); i++) {
                    bodyExamples.add(parameterValues.get(paramIndex++));
                }
                if (!bodyExamples.isEmpty()) {
                    TemplateCarouselExample bodyExample = new TemplateCarouselExample();
                    bodyExample.setBodyText(List.of(bodyExamples)); // Wrap in outer list
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
                    .map(this::toCarouselButtonDto)
                    .collect(Collectors.toList()));
            components.add(buttons);
        }

        return components;
    }

    /**
     * Parses JSON array string to List<String>.
     */
    private List<String> parseParameters(String parametersJson) {
        if (parametersJson == null || parametersJson.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(parametersJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }

    /**
     * Counts {{1}}, {{2}} style variables in text.
     */
    private int countVariables(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{\\{\\d+\\}\\}");
        java.util.regex.Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private TemplateCarouselButton toCarouselButtonDto(TemplateCarouselCardButton button) {
        TemplateCarouselButton dto = new TemplateCarouselButton();
        dto.setType(button.getType());
        dto.setText(button.getText());
        dto.setUrl(button.getUrl());
        dto.setPhoneNumber(button.getPhoneNumber());
        dto.setIndex(button.getCardButtonIndex());
        dto.setExample(parseParameters(button.getParameters()));
        return dto;
    }

    private TemplateTextDto toTextDto(TemplateText text) {
        TemplateTextDto dto = new TemplateTextDto();
        dto.setType(text.getType());
        dto.setTextIndex(text.getTextIndex());
        dto.setText(text.getText());
        return dto;
    }

    private SupportedAppDto toSupportedApp(SupportedApp supportedApp) {
        SupportedAppDto dto = new SupportedAppDto();
        dto.setPackageName(supportedApp.getPackageName());
        dto.setSignatureHash(supportedApp.getSignatureHash());
        return dto;
    }

    private TemplateExampleDto buildExampleDto(TemplateComponent component) {
        TemplateExampleDto example = new TemplateExampleDto();

        // Extract header examples if applicable
        if ("HEADER".equalsIgnoreCase(component.getType())) {
            if ("IMAGE".equalsIgnoreCase(component.getFormat()) ||
                    "VIDEO".equalsIgnoreCase(component.getFormat()) ||
                    "DOCUMENT".equalsIgnoreCase(component.getFormat())) {
                if (component.getImageUrl() != null) {
                    example.setHeaderHandle(List.of(component.getImageUrl()));
                }
            } else if ("TEXT".equalsIgnoreCase(component.getFormat())) {
                if (component.getText() != null) {
                    example.setHeaderText(List.of(component.getText()));
                }
            }
        }

        return example;
    }

    // ==================== REQUEST TO ENTITY CONVERSION ====================

    private TemplateComponent toComponent(TemplateComponentRequest req) {
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

        // Auto-generate button indices
        if (req.getButtons() != null) {
            AtomicInteger buttonIndex = new AtomicInteger(0);
            for (TemplateComponentButtonRequest btnReq : req.getButtons()) {
                comp.addButton(toButton(btnReq, buttonIndex.getAndIncrement()));
            }
        }

        if (req.getCards() != null) {
            AtomicInteger cardIndex = new AtomicInteger(0);
            for (TemplateComponentCardsRequest cardReq : req.getCards()) {
                int idx = cardReq.getIndex() != null ? cardReq.getIndex() : cardIndex.getAndIncrement();
                comp.addCarouselCard(toCarouselCard(cardReq, idx));
            }
        }

        return comp;
    }

    private TemplateComponentButton toButton(TemplateComponentButtonRequest req, int index) {
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
                btn.addSupportedApp(toSupportedApp(appReq));
            }
        }

        return btn;
    }

    private TemplateCarouselCard toCarouselCard(TemplateComponentCardsRequest req, int index) {
        TemplateCarouselCard card = TemplateCarouselCard.builder()
                .cardIndex(index)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        List<String> allParameters = new ArrayList<>();

        if (req.getComponents() != null) {
            for (TemplateCarouselCardComponentRequest compReq : req.getComponents()) {
                switch (compReq.getType().toUpperCase()) {
                    case "HEADER" -> {
                        card.setMediaType(compReq.getFormat() != null ? compReq.getFormat().getValue() : null);
                        
                        // Store actual header text (e.g., "Hello {{1}}"), not example value
                        if (compReq.getText() != null) {
                            card.setHeader(compReq.getText());
                        }
                        
                        if (compReq.getExample() != null) {
                            // Handle header handle (media URL)
                            if (compReq.getExample().getHeaderHandle() != null
                                    && !compReq.getExample().getHeaderHandle().isEmpty()) {
                                card.setImageUrl(compReq.getExample().getHeaderHandle().get(0));
                            }

                            // Collect header text example values for parameters
                            if (compReq.getExample().getHeaderText() != null
                                    && !compReq.getExample().getHeaderText().isEmpty()) {
                                allParameters.addAll(compReq.getExample().getHeaderText());
                            }
                        }
                    }
                    case "BODY" -> {
                        // Store actual body text (e.g., "Use code {{1}} to get {{2}} off.")
                        card.setBody(compReq.getText());
                        
                        // Collect body_text example values for parameters
                        if (compReq.getExample() != null 
                                && compReq.getExample().getBodyText() != null
                                && !compReq.getExample().getBodyText().isEmpty()) {
                            // body_text is List<List<String>> - take first inner list
                            List<String> bodyTexts = compReq.getExample().getBodyText().get(0);
                            allParameters.addAll(bodyTexts);
                        }
                    }
                    case "BUTTONS" -> {
                        if (compReq.getButtons() != null) {
                            for (TemplateCarouselButtonRequest btnReq : compReq.getButtons()) {
                                card.addButton(toCarouselCardButton(btnReq));
                            }
                        }
                    }
                }
            }
        }

        // Store all collected example values as JSON array in parameters
        if (!allParameters.isEmpty()) {
            card.setParameters(serializeToJsonArray(allParameters));
        }

        return card;
    }

    private TemplateCarouselCardButton toCarouselCardButton(TemplateCarouselButtonRequest req) {
        return TemplateCarouselCardButton.builder()
                .type(req.getType() != null ? req.getType().getValue() : null)
                .text(req.getText())
                .url(req.getUrl())
                .phoneNumber(req.getPhoneNumber())
                .parameters(serializeToJsonArray(req.getExample()))
                .cardButtonIndex(req.getIndex())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private TemplateText toText(TemplateTextRequest req) {
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

    private SupportedApp toSupportedApp(SupportedAppRequest req) {
        return SupportedApp.builder()
                .packageName(req.getPackageName())
                .signatureHash(req.getSignatureHash())
                .build();
    }

    public TemplateResponseDto mapToTemplateResponse(String templateId, String status, String category) {
        return TemplateResponseDto.builder()
                .metaTemplateId(templateId)
                .status(status)
                .category(category)
                .build();
    }
}