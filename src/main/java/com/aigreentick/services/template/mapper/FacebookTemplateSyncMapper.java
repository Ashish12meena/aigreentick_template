package com.aigreentick.services.template.mapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.aigreentick.services.template.dto.request.template.SupportedAppRequest;
import com.aigreentick.services.template.dto.request.template.TemplateCarouselButtonRequest;
import com.aigreentick.services.template.dto.request.template.TemplateCarouselCardComponentRequest;
import com.aigreentick.services.template.dto.request.template.TemplateCarouselExampleRequest;
import com.aigreentick.services.template.dto.request.template.TemplateComponentButtonRequest;
import com.aigreentick.services.template.dto.request.template.TemplateComponentCardsRequest;
import com.aigreentick.services.template.dto.request.template.TemplateComponentRequest;
import com.aigreentick.services.template.dto.request.template.TemplateExampleRequest;
import com.aigreentick.services.template.dto.request.template.TemplateRequest;
import com.aigreentick.services.template.dto.request.template.create.VariableDefaultButtonDto;
import com.aigreentick.services.template.dto.request.template.create.VariableDefaultCardDto;
import com.aigreentick.services.template.dto.request.template.create.VariableDefaultComponentDto;
import com.aigreentick.services.template.dto.request.template.create.VariableDefaultsDto;
import com.aigreentick.services.template.enums.ComponentType;
import com.aigreentick.services.template.model.template.SupportedApp;
import com.aigreentick.services.template.model.template.Template;
import com.aigreentick.services.template.model.template.TemplateCarouselCard;
import com.aigreentick.services.template.model.template.TemplateCarouselCardButton;
import com.aigreentick.services.template.model.template.TemplateComponent;
import com.aigreentick.services.template.model.template.TemplateComponentButton;
import com.aigreentick.services.template.model.template.TemplateText;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Converts Facebook WhatsApp API template responses to local entities.
 * Used during template sync from WhatsApp Business API.
 * 
 * Supports two modes:
 * 1. With VariableDefaultsDto - for templates that were created locally (new_created)
 *    - text = attribute name from VariableDefaultsDto
 *    - defaultValue = example value from Facebook
 * 
 * 2. Without VariableDefaultsDto - for templates created directly on Facebook
 *    - text = null
 *    - defaultValue = example value from Facebook
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FacebookTemplateSyncMapper {

    private final ObjectMapper objectMapper;

    // ==================== PUBLIC API ====================

    /**
     * Case 2: Template NOT found in DB - no attribute mapping available
     * - text = null
     * - defaultValue = example value from Facebook
     */
    public Template fromFacebookTemplate(TemplateRequest fbTemplate, Long userId, String wabaId) {
        return fromFacebookTemplateWithDefaults(fbTemplate, userId, wabaId, null);
    }

    /**
     * Case 1: Template found in DB with new_created status - has attribute mapping
     * - text = attribute name from VariableDefaultsDto
     * - defaultValue = example value from Facebook
     * 
     * @param fbTemplate Facebook template response
     * @param userId User ID
     * @param wabaId WABA ID
     * @param variableDefaults Optional attribute mappings from original request payload
     */
    public Template fromFacebookTemplateWithDefaults(
            TemplateRequest fbTemplate, 
            Long userId, 
            String wabaId,
            VariableDefaultsDto variableDefaults) {
        
        log.debug("Mapping Facebook template: {} with variableDefaults: {}", 
                fbTemplate.getName(), variableDefaults != null ? "present" : "absent");

        // Determine template type based on components
        String templateType = determineTemplateType(fbTemplate.getComponents());

        Template template = buildBaseTemplate(fbTemplate, userId, templateType);

        // Build attribute lookup map from VariableDefaultsDto
        Map<String, String> attributeMap = buildAttributeLookupMap(variableDefaults);
        log.debug("Built attribute map with {} entries", attributeMap.size());

        if (fbTemplate.getComponents() != null) {
            // Map all components first
            for (TemplateComponentRequest compReq : fbTemplate.getComponents()) {
                template.addComponent(mapToComponentEntity(compReq));
            }

            // Extract text variables from components with attribute mapping
            extractAllTextVariablesWithAttributes(fbTemplate.getComponents(), template, attributeMap);
        }

        return template;
    }

    // ==================== ATTRIBUTE LOOKUP MAP BUILDING ====================

    /**
     * Builds a lookup map from VariableDefaultsDto for quick attribute resolution.
     * Key format: TYPE_textIndex_cardIndex (cardIndex is -1 for non-carousel)
     * Value: attribute name
     */
    private Map<String, String> buildAttributeLookupMap(VariableDefaultsDto variableDefaults) {
        Map<String, String> attributeMap = new HashMap<>();
        
        if (variableDefaults == null || variableDefaults.getComponents() == null) {
            return attributeMap;
        }

        for (VariableDefaultComponentDto compDto : variableDefaults.getComponents()) {
            String type = compDto.getType();
            
            if (type == null) continue;

            switch (type.toUpperCase()) {
                case "HEADER", "BODY" -> {
                    if (compDto.getAttributes() != null) {
                        for (int i = 0; i < compDto.getAttributes().size(); i++) {
                            String attr = compDto.getAttributes().get(i);
                            // Use 1-based indexing to match template text extraction
                            String key = buildCompositeKey(type.toUpperCase(), i + 1, -1);
                            attributeMap.put(key, attr);
                            log.debug("Added attribute mapping: {} -> {}", key, attr);
                        }
                    }
                }
                case "BUTTONS" -> {
                    if (compDto.getButtons() != null) {
                        for (int btnIdx = 0; btnIdx < compDto.getButtons().size(); btnIdx++) {
                            VariableDefaultButtonDto btnDto = compDto.getButtons().get(btnIdx);
                            if (btnDto.getAttributes() != null) {
                                for (int i = 0; i < btnDto.getAttributes().size(); i++) {
                                    String attr = btnDto.getAttributes().get(i);
                                    // Button variables use 1-based indexing
                                    String key = buildCompositeKey("BUTTON", i + 1, -1);
                                    attributeMap.put(key, attr);
                                    log.debug("Added button attribute mapping: {} -> {}", key, attr);
                                }
                            }
                        }
                    }
                }
                case "CAROUSEL" -> {
                    if (compDto.getCards() != null) {
                        for (int cardIdx = 0; cardIdx < compDto.getCards().size(); cardIdx++) {
                            VariableDefaultCardDto cardDto = compDto.getCards().get(cardIdx);
                            if (cardDto.getComponents() != null) {
                                addCarouselCardAttributesToMap(cardDto.getComponents(), cardIdx, attributeMap);
                            }
                        }
                    }
                }
            }
        }

        return attributeMap;
    }

    /**
     * Add carousel card component attributes to the lookup map
     */
    private void addCarouselCardAttributesToMap(
            List<VariableDefaultComponentDto> cardComponents,
            int cardIndex,
            Map<String, String> attributeMap) {
        
        for (VariableDefaultComponentDto compDto : cardComponents) {
            String type = compDto.getType();
            if (type == null) continue;

            switch (type.toUpperCase()) {
                case "HEADER", "BODY" -> {
                    if (compDto.getAttributes() != null) {
                        for (int i = 0; i < compDto.getAttributes().size(); i++) {
                            String attr = compDto.getAttributes().get(i);
                            String key = buildCompositeKey(type.toUpperCase(), i + 1, cardIndex);
                            attributeMap.put(key, attr);
                            log.debug("Added carousel {} attribute mapping: {} -> {}", type, key, attr);
                        }
                    }
                }
                case "BUTTONS" -> {
                    if (compDto.getButtons() != null) {
                        for (int btnIdx = 0; btnIdx < compDto.getButtons().size(); btnIdx++) {
                            VariableDefaultButtonDto btnDto = compDto.getButtons().get(btnIdx);
                            if (btnDto.getAttributes() != null) {
                                for (int i = 0; i < btnDto.getAttributes().size(); i++) {
                                    String attr = btnDto.getAttributes().get(i);
                                    String key = buildCompositeKey("BUTTON", i + 1, cardIndex);
                                    attributeMap.put(key, attr);
                                    log.debug("Added carousel button attribute mapping: {} -> {}", key, attr);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Build composite key for attribute lookup
     */
    private String buildCompositeKey(String type, int textIndex, int cardIndex) {
        return String.format("%s_%d_%d", type.toUpperCase(), textIndex, cardIndex);
    }

    // ==================== TEMPLATE BUILDING ====================

    /**
     * Determines template type based on components.
     * Returns "CAROUSEL" if carousel component exists, otherwise "STANDARD".
     */
    private String determineTemplateType(List<TemplateComponentRequest> components) {
        if (components == null || components.isEmpty()) {
            return "standard";
        }

        boolean hasCarousel = components.stream()
                .anyMatch(comp -> comp.getType() == ComponentType.CAROUSEL);

        return hasCarousel ? "carousel" : "standard";
    }

    /**
     * Creates base Template entity with metadata from Facebook response.
     */
    private Template buildBaseTemplate(TemplateRequest req, Long userId, String templateType) {
        return Template.builder()
                .userId(userId)
                .name(req.getName())
                .language(req.getLanguage())
                .category(req.getCategory())
                .previousCategory(req.getPreviousCategory())
                .status(req.getStatus() != null ? req.getStatus().getValue() : "PENDING")
                .waId(req.getMetaTemplateId())
                .templateType(templateType)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ==================== COMPONENT MAPPING ====================

    /**
     * Maps Facebook component to TemplateComponent entity.
     * Handles buttons and carousel cards if present.
     */
    private TemplateComponent mapToComponentEntity(TemplateComponentRequest req) {
        String imageUrl = null;

        if (req.getExample() != null) {
            if (req.getExample().getHeaderHandle() != null
                    && !req.getExample().getHeaderHandle().isEmpty()) {
                imageUrl = req.getExample().getHeaderHandle().get(0);
            }
        }
        TemplateComponent comp = TemplateComponent.builder()
                .type(req.getType() != null ? req.getType().toString() : null)
                .format(req.getFormat())
                .text(req.getText())
                .imageUrl(imageUrl)
                .addSecurityRecommendation(req.getAddSecurityRecommendation())
                .codeExpirationMinutes(req.getCodeExpirationMinutes())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Map buttons if present
        if (req.getButtons() != null) {
            AtomicInteger btnIndex = new AtomicInteger(0);
            for (TemplateComponentButtonRequest btnReq : req.getButtons()) {
                int index = btnReq.getIndex() != null ? btnReq.getIndex() : btnIndex.getAndIncrement();
                comp.addButton(mapToButtonEntity(btnReq, index));
            }
        }

        // Map carousel cards if present
        if (req.getCards() != null) {
            List<TemplateComponentCardsRequest> cards = req.getCards();
            for (int i = 0; i < cards.size(); i++) {
                TemplateComponentCardsRequest cardReq = cards.get(i);
                int index = cardReq.getIndex() != null ? cardReq.getIndex() : i;
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

    private SupportedApp mapToSupportedAppEntity(SupportedAppRequest req) {
        return SupportedApp.builder()
                .packageName(req.getPackageName())
                .signatureHash(req.getSignatureHash())
                .build();
    }

    // ==================== CAROUSEL CARD MAPPING ====================

    /**
     * Maps carousel card request to TemplateCarouselCard entity.
     * Processes header, body, and buttons within the card.
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

        if (!allParameters.isEmpty()) {
            card.setParameters(serializeToJson(allParameters));
        } else {
            card.setParameters("[]");
        }

        return card;
    }

    /**
     * Processes individual carousel card component (HEADER, BODY, BUTTONS).
     */
    private void processCarouselCardComponent(
            TemplateCarouselCardComponentRequest compReq,
            TemplateCarouselCard card,
            List<String> allParameters) {

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

    // ==================== TEXT VARIABLE EXTRACTION WITH ATTRIBUTES ====================

    /**
     * Extracts all text variables from components with attribute mapping support.
     * 
     * If attributeMap has a value for the composite key:
     *   - text = attribute name (from attributeMap)
     *   - defaultValue = example value (from Facebook)
     * 
     * If attributeMap does NOT have a value:
     *   - text = null
     *   - defaultValue = example value (from Facebook)
     */
    private void extractAllTextVariablesWithAttributes(
            List<TemplateComponentRequest> components, 
            Template template,
            Map<String, String> attributeMap) {
        
        if (components == null) return;

        for (int i = 0; i < components.size(); i++) {
            TemplateComponentRequest componentReq = components.get(i);
            ComponentType type = componentReq.getType();
            TemplateComponent componentEntity = template.getComponents().get(i);

            if (type == ComponentType.CAROUSEL) {
                extractCarouselTextVariablesWithAttributes(componentReq, template, componentEntity, attributeMap);
            } else {
                extractRegularTextVariablesWithAttributes(componentReq, template, componentEntity, attributeMap);
            }
        }
    }

    /**
     * Extracts variables from non-carousel components (HEADER, BODY, BUTTONS) with attribute mapping.
     */
    private void extractRegularTextVariablesWithAttributes(
            TemplateComponentRequest component,
            Template template,
            TemplateComponent componentEntity,
            Map<String, String> attributeMap) {

        ComponentType type = component.getType();
        TemplateExampleRequest example = component.getExample();

        switch (type) {
            case HEADER -> extractHeaderTextVariablesWithAttributes(example, template, componentEntity, attributeMap);
            case BODY -> extractBodyTextVariablesWithAttributes(example, template, componentEntity, attributeMap);
            case BUTTONS -> extractButtonTextVariablesWithAttributes(component.getButtons(), template, componentEntity, attributeMap);
            default -> { /* FOOTER, LIMITED_TIME_OFFER - no variables */ }
        }
    }

    /**
     * Extracts HEADER text variables with attribute mapping.
     */
    private void extractHeaderTextVariablesWithAttributes(
            TemplateExampleRequest example,
            Template template,
            TemplateComponent component,
            Map<String, String> attributeMap) {

        if (example == null || example.getHeaderText() == null) return;

        List<String> headerTexts = example.getHeaderText();
        for (int i = 0; i < headerTexts.size(); i++) {
            int textIndex = i + 1; // 1-based indexing
            String exampleValue = headerTexts.get(i);
            String compositeKey = buildCompositeKey("HEADER", textIndex, -1);
            String attributeName = attributeMap.get(compositeKey);

            template.addText(buildTemplateTextWithAttribute(
                    "HEADER", attributeName, exampleValue, textIndex, false, null, component));
        }
        log.debug("Extracted {} HEADER variables with attribute mapping", headerTexts.size());
    }

    /**
     * Extracts BODY text variables with attribute mapping.
     */
    private void extractBodyTextVariablesWithAttributes(
            TemplateExampleRequest example,
            Template template,
            TemplateComponent component,
            Map<String, String> attributeMap) {

        if (example == null || example.getBodyText() == null || example.getBodyText().isEmpty()) return;

        List<String> bodyTexts = example.getBodyText().get(0);
        for (int i = 0; i < bodyTexts.size(); i++) {
            int textIndex = i + 1; // 1-based indexing
            String exampleValue = bodyTexts.get(i);
            String compositeKey = buildCompositeKey("BODY", textIndex, -1);
            String attributeName = attributeMap.get(compositeKey);

            template.addText(buildTemplateTextWithAttribute(
                    "BODY", attributeName, exampleValue, textIndex, false, null, component));
        }
        log.debug("Extracted {} BODY variables with attribute mapping", bodyTexts.size());
    }

    /**
     * Extracts BUTTON text variables with attribute mapping.
     */
    private void extractButtonTextVariablesWithAttributes(
            List<TemplateComponentButtonRequest> buttons,
            Template template,
            TemplateComponent component,
            Map<String, String> attributeMap) {

        if (buttons == null) return;

        for (TemplateComponentButtonRequest btn : buttons) {
            if (btn.getExample() == null || btn.getExample().isEmpty()) continue;

            List<String> examples = btn.getExample();
            for (int i = 0; i < examples.size(); i++) {
                int textIndex = i + 1; // 1-based indexing
                String exampleValue = examples.get(i);
                String compositeKey = buildCompositeKey("BUTTON", textIndex, -1);
                String attributeName = attributeMap.get(compositeKey);

                template.addText(buildTemplateTextWithAttribute(
                        "BUTTON", attributeName, exampleValue, textIndex, false, null, component));
            }
            log.debug("Extracted {} BUTTON variables with attribute mapping", examples.size());
        }
    }

    // ==================== CAROUSEL TEXT EXTRACTION WITH ATTRIBUTES ====================

    /**
     * Extracts text variables from carousel cards with attribute mapping.
     */
    private void extractCarouselTextVariablesWithAttributes(
            TemplateComponentRequest component,
            Template template,
            TemplateComponent carouselComponent,
            Map<String, String> attributeMap) {

        if (component.getCards() == null) return;

        List<TemplateComponentCardsRequest> cards = component.getCards();

        for (int i = 0; i < cards.size(); i++) {
            TemplateComponentCardsRequest card = cards.get(i);
            int cardIndex = card.getIndex() != null ? card.getIndex() : i;

            if (card.getComponents() == null) continue;

            for (TemplateCarouselCardComponentRequest cardComp : card.getComponents()) {
                String compType = cardComp.getType().toUpperCase();

                switch (compType) {
                    case "HEADER" -> extractCarouselHeaderTextsWithAttributes(
                            cardComp, cardIndex, template, carouselComponent, attributeMap);
                    case "BODY" -> extractCarouselBodyTextsWithAttributes(
                            cardComp, cardIndex, template, carouselComponent, attributeMap);
                    case "BUTTONS" -> extractCarouselButtonTextsWithAttributes(
                            cardComp, cardIndex, template, carouselComponent, attributeMap);
                }
            }
        }
    }

    private void extractCarouselHeaderTextsWithAttributes(
            TemplateCarouselCardComponentRequest cardComp,
            int cardIndex,
            Template template,
            TemplateComponent carouselComponent,
            Map<String, String> attributeMap) {

        if (cardComp.getExample() == null) return;

        TemplateCarouselExampleRequest example = cardComp.getExample();
        if (example.getHeaderText() != null && !example.getHeaderText().isEmpty()) {
            List<String> headerTexts = example.getHeaderText();
            for (int i = 0; i < headerTexts.size(); i++) {
                int textIndex = i + 1;
                String exampleValue = headerTexts.get(i);
                String compositeKey = buildCompositeKey("HEADER", textIndex, cardIndex);
                String attributeName = attributeMap.get(compositeKey);

                template.addText(buildTemplateTextWithAttribute(
                        "HEADER", attributeName, exampleValue, textIndex, true, cardIndex, carouselComponent));
            }
            log.debug("Extracted {} HEADER variables for card {} with attribute mapping", 
                    headerTexts.size(), cardIndex);
        }
    }

    private void extractCarouselBodyTextsWithAttributes(
            TemplateCarouselCardComponentRequest cardComp,
            int cardIndex,
            Template template,
            TemplateComponent carouselComponent,
            Map<String, String> attributeMap) {

        if (cardComp.getExample() == null) return;

        TemplateCarouselExampleRequest example = cardComp.getExample();
        if (example.getBodyText() != null && !example.getBodyText().isEmpty()) {
            List<String> bodyTexts = example.getBodyText().get(0);
            for (int i = 0; i < bodyTexts.size(); i++) {
                int textIndex = i + 1;
                String exampleValue = bodyTexts.get(i);
                String compositeKey = buildCompositeKey("BODY", textIndex, cardIndex);
                String attributeName = attributeMap.get(compositeKey);

                template.addText(buildTemplateTextWithAttribute(
                        "BODY", attributeName, exampleValue, textIndex, true, cardIndex, carouselComponent));
            }
            log.debug("Extracted {} BODY variables for card {} with attribute mapping", 
                    bodyTexts.size(), cardIndex);
        }
    }

    private void extractCarouselButtonTextsWithAttributes(
            TemplateCarouselCardComponentRequest cardComp,
            int cardIndex,
            Template template,
            TemplateComponent carouselComponent,
            Map<String, String> attributeMap) {

        if (cardComp.getButtons() == null) return;

        for (TemplateCarouselButtonRequest btn : cardComp.getButtons()) {
            if (btn.getExample() == null || btn.getExample().isEmpty()) continue;

            List<String> examples = btn.getExample();
            for (int i = 0; i < examples.size(); i++) {
                int textIndex = i + 1;
                String exampleValue = examples.get(i);
                String compositeKey = buildCompositeKey("BUTTON", textIndex, cardIndex);
                String attributeName = attributeMap.get(compositeKey);

                template.addText(buildTemplateTextWithAttribute(
                        "BUTTON", attributeName, exampleValue, textIndex, true, cardIndex, carouselComponent));
            }
            log.debug("Extracted {} BUTTON variables for card {} with attribute mapping", 
                    examples.size(), cardIndex);
        }
    }

    // ==================== HELPER METHODS ====================

    /**
     * Creates TemplateText entity with attribute mapping support.
     * 
     * @param type Component type (HEADER, BODY, BUTTON)
     * @param attributeName Attribute name from VariableDefaultsDto (can be null)
     * @param exampleValue Example value from Facebook
     * @param textIndex 1-based index
     * @param isCarousel Whether this is a carousel variable
     * @param cardIndex Card index for carousel (null for non-carousel)
     * @param component Parent component
     */
    private TemplateText buildTemplateTextWithAttribute(
            String type,
            String attributeName,
            String exampleValue,
            int textIndex,
            boolean isCarousel,
            Integer cardIndex,
            TemplateComponent component) {

        return TemplateText.builder()
                .type(type)
                .text(attributeName)           // attribute name (null if not available)
                .defaultValue(exampleValue)    // example value from Facebook
                .textIndex(textIndex)
                .isCarousel(isCarousel)
                .cardIndex(cardIndex)
                .component(component)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Serializes list to JSON string for storage.
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
}