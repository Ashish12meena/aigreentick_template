package com.aigreentick.services.template.mapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FacebookTemplateSyncMapper {

    private final ObjectMapper objectMapper;

    // ==================== PUBLIC API ====================

    /**
     * Main method to convert Facebook template response to Template entity.
     * Creates components and extracts text variables with example values.
     */
    public Template fromFacebookTemplate(TemplateRequest fbTemplate, Long userId, String wabaId) {
        // Determine template type based on components
        String templateType = determineTemplateType(fbTemplate.getComponents());
        
        Template template = buildBaseTemplate(fbTemplate, userId, templateType);

        if (fbTemplate.getComponents() != null) {
            // Map all components first
            for (TemplateComponentRequest compReq : fbTemplate.getComponents()) {
                template.addComponent(mapToComponentEntity(compReq));
            }

            // Extract text variables from components
            extractAllTextVariables(fbTemplate.getComponents(), template);
        }

        return template;
    }

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

    // ==================== TEMPLATE BUILDING ====================

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
        }else{
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

    // ==================== TEXT VARIABLE EXTRACTION ====================

    /**
     * Extracts all text variables from components.
     * Variables are stored with example values as fallbacks.
     */
    private void extractAllTextVariables(List<TemplateComponentRequest> components, Template template) {
        if (components == null) return;

        for (int i = 0; i < components.size(); i++) {
            TemplateComponentRequest componentReq = components.get(i);
            ComponentType type = componentReq.getType();
            TemplateComponent componentEntity = template.getComponents().get(i);

            if (type == ComponentType.CAROUSEL) {
                extractCarouselTextVariables(componentReq, template, componentEntity);
            } else {
                extractRegularTextVariables(componentReq, template, componentEntity);
            }
        }
    }

    /**
     * Extracts variables from non-carousel components (HEADER, BODY, BUTTONS).
     */
    private void extractRegularTextVariables(
            TemplateComponentRequest component,
            Template template,
            TemplateComponent componentEntity) {

        ComponentType type = component.getType();
        TemplateExampleRequest example = component.getExample();

        switch (type) {
            case HEADER -> extractHeaderTextVariables(example, template, componentEntity);
            case BODY -> extractBodyTextVariables(example, template, componentEntity);
            case BUTTONS -> extractButtonTextVariables(component.getButtons(), template, componentEntity);
            default -> { /* FOOTER, LIMITED_TIME_OFFER - no variables */ }
        }
    }

    /**
     * Extracts HEADER text variables from example values.
     */
    private void extractHeaderTextVariables(
            TemplateExampleRequest example,
            Template template,
            TemplateComponent component) {

        if (example == null || example.getHeaderText() == null) return;

        List<String> headerTexts = example.getHeaderText();
        for (int i = 0; i < headerTexts.size(); i++) {
            template.addText(buildTemplateText("HEADER", headerTexts.get(i), i, false, null, component));
        }
        log.debug("Extracted {} HEADER variables", headerTexts.size());
    }

    /**
     * Extracts BODY text variables from example values.
     */
    private void extractBodyTextVariables(
            TemplateExampleRequest example,
            Template template,
            TemplateComponent component) {

        if (example == null || example.getBodyText() == null || example.getBodyText().isEmpty()) return;

        List<String> bodyTexts = example.getBodyText().get(0);
        for (int i = 0; i < bodyTexts.size(); i++) {
            template.addText(buildTemplateText("BODY", bodyTexts.get(i), i, false, null, component));
        }
        log.debug("Extracted {} BODY variables", bodyTexts.size());
    }

    /**
     * Extracts BUTTON text variables (for URL buttons with dynamic suffix).
     */
    private void extractButtonTextVariables(
            List<TemplateComponentButtonRequest> buttons,
            Template template,
            TemplateComponent component) {

        if (buttons == null) return;

        for (TemplateComponentButtonRequest btn : buttons) {
            if (btn.getExample() == null || btn.getExample().isEmpty()) continue;

            int buttonIndex = btn.getIndex() != null ? btn.getIndex() : 0;
            List<String> examples = btn.getExample();

            for (int i = 0; i < examples.size(); i++) {
                template.addText(buildTemplateText("BUTTON", examples.get(i), i, false, null, component));
            }
            log.debug("Extracted {} BUTTON variables for button {}", examples.size(), buttonIndex);
        }
    }

    // ==================== CAROUSEL TEXT EXTRACTION ====================

    /**
     * Extracts text variables from carousel cards.
     * Each variable includes cardIndex for card-specific resolution.
     */
    private void extractCarouselTextVariables(
            TemplateComponentRequest component,
            Template template,
            TemplateComponent carouselComponent) {

        if (component.getCards() == null) return;

        List<TemplateComponentCardsRequest> cards = component.getCards();

        for (int i = 0; i < cards.size(); i++) {
            TemplateComponentCardsRequest card = cards.get(i);
            int cardIndex = card.getIndex() != null ? card.getIndex() : i;

            if (card.getComponents() == null) continue;

            for (TemplateCarouselCardComponentRequest cardComp : card.getComponents()) {
                String compType = cardComp.getType().toUpperCase();

                switch (compType) {
                    case "HEADER" -> extractCarouselHeaderTexts(cardComp, cardIndex, template, carouselComponent);
                    case "BODY" -> extractCarouselBodyTexts(cardComp, cardIndex, template, carouselComponent);
                    case "BUTTONS" -> extractCarouselButtonTexts(cardComp, cardIndex, template, carouselComponent);
                }
            }
        }
    }

    private void extractCarouselHeaderTexts(
            TemplateCarouselCardComponentRequest cardComp,
            int cardIndex,
            Template template,
            TemplateComponent carouselComponent) {

        if (cardComp.getExample() == null) return;

        TemplateCarouselExampleRequest example = cardComp.getExample();
        if (example.getHeaderText() != null && !example.getHeaderText().isEmpty()) {
            List<String> headerTexts = example.getHeaderText();
            for (int i = 0; i < headerTexts.size(); i++) {
                template.addText(buildTemplateText("HEADER", headerTexts.get(i), i, true, cardIndex, carouselComponent));
            }
            log.debug("Extracted {} HEADER variables for card {}", headerTexts.size(), cardIndex);
        }
    }

    private void extractCarouselBodyTexts(
            TemplateCarouselCardComponentRequest cardComp,
            int cardIndex,
            Template template,
            TemplateComponent carouselComponent) {

        if (cardComp.getExample() == null) return;

        TemplateCarouselExampleRequest example = cardComp.getExample();
        if (example.getBodyText() != null && !example.getBodyText().isEmpty()) {
            List<String> bodyTexts = example.getBodyText().get(0);
            for (int i = 0; i < bodyTexts.size(); i++) {
                template.addText(buildTemplateText("BODY", bodyTexts.get(i), i, true, cardIndex, carouselComponent));
            }
            log.debug("Extracted {} BODY variables for card {}", bodyTexts.size(), cardIndex);
        }
    }

    private void extractCarouselButtonTexts(
            TemplateCarouselCardComponentRequest cardComp,
            int cardIndex,
            Template template,
            TemplateComponent carouselComponent) {

        if (cardComp.getButtons() == null) return;

        for (TemplateCarouselButtonRequest btn : cardComp.getButtons()) {
            if (btn.getExample() == null || btn.getExample().isEmpty()) continue;

            int buttonIndex = btn.getIndex() != null ? btn.getIndex() : 0;
            List<String> examples = btn.getExample();

            for (int i = 0; i < examples.size(); i++) {
                template.addText(buildTemplateText("BUTTON", examples.get(i), buttonIndex, true, cardIndex, carouselComponent));
            }
            log.debug("Extracted {} BUTTON variables for card {} button {}", examples.size(), cardIndex, buttonIndex);
        }
    }

    // ==================== HELPER METHODS ====================

    /**
     * Creates TemplateText entity for storing variable info.
     * - text: stores example value from Facebook (used as fallback)
     * - defaultValue: null initially (user configures later)
     */
    private TemplateText buildTemplateText(
            String type,
            String exampleValue,
            int textIndex,
            boolean isCarousel,
            Integer cardIndex,
            TemplateComponent component) {

        return TemplateText.builder()
                .type(type)
                .text(exampleValue)
                .textIndex(textIndex)
                .isCarousel(isCarousel)
                .cardIndex(cardIndex)
                .defaultValue(null)
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