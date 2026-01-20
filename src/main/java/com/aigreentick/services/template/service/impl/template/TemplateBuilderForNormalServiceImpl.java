package com.aigreentick.services.template.service.impl.template;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.aigreentick.services.template.constants.MessageConstants;
import com.aigreentick.services.template.dto.build.*;
import com.aigreentick.services.template.dto.request.template.csv.CarouselButtonDto;
import com.aigreentick.services.template.dto.request.template.csv.CarouselCardDto;
import com.aigreentick.services.template.dto.request.template.normal.SendTemplateNormalRequestDto;
import com.aigreentick.services.template.enums.ButtonTypes;
import com.aigreentick.services.template.enums.ComponentType;
import com.aigreentick.services.template.enums.MediaType;
import com.aigreentick.services.template.exceptions.CarouselConfigurationException;
import com.aigreentick.services.template.exceptions.InvalidMediaType;
import com.aigreentick.services.template.exceptions.InvalidTemplateComponentType;
import com.aigreentick.services.template.service.impl.contact.ChatContactServiceImpl;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Builds WhatsApp API message payloads for Normal Broadcast flow.
 * 
 * Key differences from CSV flow:
 * - Non-carousel variables come as comma-separated string (same for ALL contacts)
 * - No per-contact variable override for non-carousel
 * - No button variables for non-carousel templates
 * - Carousel variables use same CarouselCardDto structure as CSV
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TemplateBuilderForNormalServiceImpl {

    private final ChatContactServiceImpl chatContactService;

    private static final int MAX_CARDS = MessageConstants.MAX_CARDS;
    private static final int MAX_BUTTONS_PER_CARD = MessageConstants.MAX_BUTTONS_PER_CARD;

    /**
     * Build WhatsApp API message payloads for all phone numbers.
     * Since normal flow has same variables for all contacts, the template
     * components are identical - only the "to" field differs.
     */
    public List<MessageRequest> buildSendableTemplatesFromNormal(
            Long userId,
            List<String> phoneNumbers,
            TemplateDto template,
            SendTemplateNormalRequestDto request) {

        log.info("Building {} messages from Normal request for template: {}", 
                phoneNumbers.size(), template.getName());

        // Build parameter context
        NormalParameterContext ctx = buildParameterContext(userId, phoneNumbers, template, request);

        return phoneNumbers.stream()
                .map(phone -> buildMessageRequest(phone, template, ctx, request))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ==================== CONTEXT BUILDING ====================

    private NormalParameterContext buildParameterContext(
            Long userId,
            List<String> phoneNumbers,
            TemplateDto template,
            SendTemplateNormalRequestDto request) {

        // 1. Parse comma-separated variables string
        Map<Integer, String> globalVars = parseVariablesString(request.getVariables());
        log.debug("Parsed {} global variables from string", globalVars.size());

        // 2. Build fallback values from template
        Map<String, String> fallbacks = buildFallbackValues(template);

        // 3. Parse carousel parameters (same as CSV)
        Map<Integer, CardParameters> carousel = parseCarouselParams(request.getCarouselCards());

        // 4. Fetch contact attributes for fallback personalization
        List<String> attrKeys = extractAttributeKeys(template);
        Map<String, Map<String, String>> contactAttrs = attrKeys.isEmpty()
                ? new HashMap<>()
                : chatContactService.getContactAttributes(userId, phoneNumbers, attrKeys);

        return NormalParameterContext.builder()
                .globalVariables(globalVars)
                .fallbackValues(fallbacks)
                .carouselParameters(carousel)
                .contactAttributes(contactAttrs)
                .build();
    }

    /**
     * Parse comma-separated variables string into indexed map.
     * Example: "John,Doe,Premium" -> {1: "John", 2: "Doe", 3: "Premium"}
     * 
     * Empty values are preserved: "John,,Premium" -> {1: "John", 2: "", 3: "Premium"}
     */
    private Map<Integer, String> parseVariablesString(String variables) {
        Map<Integer, String> result = new HashMap<>();
        if (variables == null || variables.isBlank()) {
            return result;
        }

        String[] parts = variables.split(",", -1); // -1 to keep trailing empty strings
        for (int i = 0; i < parts.length; i++) {
            result.put(i + 1, parts[i].trim()); // 1-based index
        }

        log.debug("Parsed variables string: {} -> {}", variables, result);
        return result;
    }

    /**
     * Build fallback values map from template texts.
     * Priority: defaultValue > text (example value)
     */
    private Map<String, String> buildFallbackValues(TemplateDto template) {
        Map<String, String> fallbacks = new HashMap<>();
        if (template.getTexts() == null) return fallbacks;

        for (TemplateTextDto t : template.getTexts()) {
            String key = compositeKey(t.getType(), t.getTextIndex(), t.getCardIndex(), t.getIsCarousel());
            String val = t.getDefaultValue();
            if (val == null || val.isBlank()) {
                val = t.getText();
            }
            fallbacks.put(key, val != null ? val : "");
        }
        return fallbacks;
    }

    /**
     * Parse carousel card parameters (same structure as CSV).
     */
    private Map<Integer, CardParameters> parseCarouselParams(List<CarouselCardDto> cards) {
        Map<Integer, CardParameters> result = new HashMap<>();
        if (cards == null) return result;

        for (CarouselCardDto card : cards) {
            if (card.getCardIndex() == null) continue;

            result.put(card.getCardIndex(), CardParameters.builder()
                    .bodyVariables(parseStringKeys(card.getVariables()))
                    .buttonVariables(parseButtonVars(card.getButtons()))
                    .imageUrl(card.getImageUrl())
                    .build());
        }
        return result;
    }

    private Map<Integer, Map<Integer, String>> parseButtonVars(List<CarouselButtonDto> buttons) {
        Map<Integer, Map<Integer, String>> result = new HashMap<>();
        if (buttons == null) return result;

        for (int i = 0; i < buttons.size(); i++) {
            if (buttons.get(i).getVariables() != null) {
                result.put(i, parseStringKeys(buttons.get(i).getVariables()));
            }
        }
        return result;
    }

    private Map<Integer, String> parseStringKeys(Map<String, String> input) {
        Map<Integer, String> result = new HashMap<>();
        if (input == null) return result;
        input.forEach((k, v) -> {
            try {
                result.put(Integer.parseInt(k), v);
            } catch (NumberFormatException ignored) {
            }
        });
        return result;
    }

    /**
     * Extract attribute keys that might be stored in defaultValue field
     * for contact attribute mapping.
     */
    private List<String> extractAttributeKeys(TemplateDto template) {
        if (template.getTexts() == null) return Collections.emptyList();
        return template.getTexts().stream()
                .map(TemplateTextDto::getDefaultValue)
                .filter(v -> v != null && !v.isBlank())
                .distinct()
                .toList();
    }

    // ==================== MESSAGE BUILDING ====================

    private MessageRequest buildMessageRequest(
            String phone,
            TemplateDto template,
            NormalParameterContext ctx,
            SendTemplateNormalRequestDto request) {

        MessageRequest req = new MessageRequest();
        req.setTo(phone);
        req.setType("template");

        SendableTemplate sendable = new SendableTemplate();
        sendable.setName(template.getName());
        sendable.setLanguage(new Language(template.getLanguage()));

        List<Component> components = new ArrayList<>();

        for (TemplateComponentDto comp : template.getComponents()) {
            switch (ComponentType.fromValue(comp.getType())) {
                case HEADER -> addIfNotNull(components, buildHeader(comp, template, phone, ctx, request));
                case BODY -> addIfNotNull(components, buildBody(template, phone, ctx));
                case BUTTONS -> addAll(components, buildButtons(comp));
                case CAROUSEL -> addIfNotNull(components, buildCarousel(comp, template, ctx, request));
                case LIMITED_TIME_OFFER -> {} // Not supported in normal flow
                default -> throw new InvalidTemplateComponentType("Unsupported: " + comp.getType());
            }
        }

        sendable.setComponents(components.isEmpty() ? null : components);
        req.setTemplate(sendable);
        return req;
    }

    // ==================== HEADER COMPONENT ====================

    private Component buildHeader(
            TemplateComponentDto comp,
            TemplateDto template,
            String phone,
            NormalParameterContext ctx,
            SendTemplateNormalRequestDto request) {

        if ("TEXT".equalsIgnoreCase(comp.getFormat())) {
            return buildHeaderText(template, phone, ctx);
        }
        return buildHeaderMedia(comp, request);
    }

    private Component buildHeaderText(TemplateDto template, String phone, NormalParameterContext ctx) {
        List<TemplateTextDto> texts = filterTexts(template, "HEADER", null);
        if (texts.isEmpty()) return null;

        List<Parameter> params = texts.stream()
                .map(t -> resolveNonCarouselValue(t, phone, ctx))
                .filter(v -> !v.isEmpty())
                .map(this::textParam)
                .collect(Collectors.toList());

        if (params.isEmpty()) return null;

        Component c = new Component();
        c.setType("header");
        c.setParameters(params);
        return c;
    }

    private Component buildHeaderMedia(TemplateComponentDto comp, SendTemplateNormalRequestDto request) {
        String url = resolveMediaUrl(comp, request);
        if (url == null) return null;

        MediaType type = MediaType.fromValue(comp.getFormat());
        Component c = new Component();
        c.setType("header");
        c.setParameters(List.of(mediaParam(type, url)));
        return c;
    }

    // ==================== BODY COMPONENT ====================

    private Component buildBody(TemplateDto template, String phone, NormalParameterContext ctx) {
        List<TemplateTextDto> texts = filterTexts(template, "BODY", null);
        if (texts.isEmpty()) return null;

        List<Parameter> params = texts.stream()
                .map(t -> textParam(resolveNonCarouselValue(t, phone, ctx)))
                .collect(Collectors.toList());

        if (params.isEmpty()) return null;

        Component c = new Component();
        c.setType("body");
        c.setParameters(params);
        return c;
    }

    // ==================== BUTTONS COMPONENT (NON-CAROUSEL) ====================

    /**
     * Build button components for non-carousel templates.
     * NOTE: Normal flow does NOT support URL button variables for non-carousel.
     * Only QUICK_REPLY and static buttons are handled.
     */
    private List<Component> buildButtons(TemplateComponentDto comp) {
        List<Component> result = new ArrayList<>();
        if (comp.getButtons() == null) return result;

        // Non-carousel buttons in normal flow have no variable support
        // Only handle QUICK_REPLY with static payload
        for (int i = 0; i < comp.getButtons().size(); i++) {
            TemplateComponentButtonDto btn = comp.getButtons().get(i);
            ButtonTypes type = ButtonTypes.fromValue(btn.getType());

            if (type == ButtonTypes.QUICK_REPLY) {
                Parameter p = new Parameter();
                p.setType("payload");
                p.setPayload("payload");

                Component c = new Component();
                c.setType("button");
                c.setSubType("quick_reply");
                c.setIndex(String.valueOf(i));
                c.setParameters(List.of(p));
                result.add(c);
            }
            // URL buttons without variables are not added (no dynamic part)
        }
        return result;
    }

    // ==================== CAROUSEL COMPONENT ====================

    private Component buildCarousel(
            TemplateComponentDto comp,
            TemplateDto template,
            NormalParameterContext ctx,
            SendTemplateNormalRequestDto request) {

        if (comp.getCards() == null || comp.getCards().isEmpty()) {
            throw new CarouselConfigurationException("Carousel must have cards");
        }
        if (comp.getCards().size() > MAX_CARDS) {
            throw new CarouselConfigurationException("Max " + MAX_CARDS + " cards");
        }

        Component c = new Component();
        c.setType("carousel");
        c.setCards(comp.getCards().stream()
                .map(card -> buildCard(card, template, ctx, request))
                .collect(Collectors.toList()));
        return c;
    }

    private Card buildCard(
            TemplateComponentCardsDto templateCard,
            TemplateDto template,
            NormalParameterContext ctx,
            SendTemplateNormalRequestDto request) {

        Card card = new Card();
        Integer idx = templateCard.getIndex();
        card.setCardIndex(idx);

        CardParameters cardParams = ctx.getCarouselParameters()
                .getOrDefault(idx, CardParameters.empty());

        List<CarouselComponent> comps = new ArrayList<>();
        for (TemplateCarouselCardComponent comp : templateCard.getComponents()) {
            comps.addAll(buildCardComponent(comp, template, idx, cardParams, ctx, request));
        }
        card.setComponents(comps);
        return card;
    }

    private List<CarouselComponent> buildCardComponent(
            TemplateCarouselCardComponent comp,
            TemplateDto template,
            Integer cardIdx,
            CardParameters cardParams,
            NormalParameterContext ctx,
            SendTemplateNormalRequestDto request) {

        return switch (ComponentType.fromValue(comp.getType())) {
            case HEADER -> optList(buildCarouselHeader(comp, cardIdx, cardParams, request));
            case BODY -> optList(buildCarouselBody(template, cardIdx, cardParams, ctx));
            case BUTTONS -> buildCarouselButtons(comp, cardIdx, cardParams);
            default -> throw new InvalidTemplateComponentType("Unsupported: " + comp.getType());
        };
    }

    private CarouselComponent buildCarouselHeader(
            TemplateCarouselCardComponent comp,
            Integer cardIdx,
            CardParameters cardParams,
            SendTemplateNormalRequestDto request) {

        // Priority: cardParams.imageUrl > template example
        String url = cardParams.getImageUrl();
        if (url == null || url.isBlank()) {
            url = extractCarouselMediaUrl(comp);
        }
        if (url == null) return null;

        MediaType type = MediaType.fromValue(comp.getFormat());
        CarouselComponent c = new CarouselComponent();
        c.setType("header");
        c.setParameters(List.of(mediaParam(type, url)));
        return c;
    }

    private CarouselComponent buildCarouselBody(
            TemplateDto template,
            Integer cardIdx,
            CardParameters cardParams,
            NormalParameterContext ctx) {

        List<TemplateTextDto> texts = filterTexts(template, "BODY", cardIdx);
        if (texts.isEmpty()) return null;

        List<Parameter> params = new ArrayList<>();
        for (TemplateTextDto t : texts) {
            int varIdx = t.getTextIndex() != null ? t.getTextIndex() : 0;

            // Priority: cardParams > fallback
            String val = cardParams.getBodyVariables().get(varIdx + 1); // 1-based in request
            if (val == null || val.isBlank()) {
                val = ctx.getFallbackValues().getOrDefault(
                        compositeKey(t.getType(), t.getTextIndex(), cardIdx, true), "");
            }
            params.add(textParam(val));
        }

        if (params.isEmpty()) return null;
        CarouselComponent c = new CarouselComponent();
        c.setType("body");
        c.setParameters(params);
        return c;
    }

    private List<CarouselComponent> buildCarouselButtons(
            TemplateCarouselCardComponent comp,
            Integer cardIdx,
            CardParameters cardParams) {

        List<TemplateCarouselButton> buttons = Optional.ofNullable(comp.getButtons())
                .orElse(Collections.emptyList());

        if (buttons.size() > MAX_BUTTONS_PER_CARD) {
            throw new CarouselConfigurationException("Max " + MAX_BUTTONS_PER_CARD + " buttons per card");
        }

        List<CarouselComponent> result = new ArrayList<>();
        for (int i = 0; i < buttons.size(); i++) {
            TemplateCarouselButton btn = buttons.get(i);
            ButtonTypes type = ButtonTypes.fromValue(btn.getType());

            if (type == ButtonTypes.URL) {
                Map<Integer, String> btnVars = cardParams.getButtonVariables()
                        .getOrDefault(i, new HashMap<>());
                String val = btnVars.get(1);

                // Fallback to example
                if (val == null && btn.getExample() != null && !btn.getExample().isEmpty()) {
                    val = btn.getExample().get(0);
                }

                if (val != null && !val.isBlank()) {
                    CarouselComponent c = new CarouselComponent();
                    c.setType("button");
                    c.setSubType("url");
                    c.setIndex(i);
                    c.setParameters(List.of(textParam(val)));
                    result.add(c);
                }
            } else if (type == ButtonTypes.QUICK_REPLY) {
                Parameter p = new Parameter();
                p.setType("payload");
                p.setPayload("payload");

                CarouselComponent c = new CarouselComponent();
                c.setType("button");
                c.setSubType("quick_reply");
                c.setIndex(i);
                c.setParameters(List.of(p));
                result.add(c);
            }
        }
        return result;
    }

    // ==================== VALUE RESOLUTION ====================

    /**
     * Resolve non-carousel variable value.
     * Priority: Global Variables > Contact Attribute > Fallback
     */
    private String resolveNonCarouselValue(TemplateTextDto text, String phone, NormalParameterContext ctx) {
        int varIdx = text.getTextIndex() != null ? text.getTextIndex() : 0;
        String key = compositeKey(text.getType(), text.getTextIndex(), text.getCardIndex(), text.getIsCarousel());

        // 1. Global variables (from comma-separated string) - 1-based index
        String globalVal = ctx.getGlobalVariables().get(varIdx + 1);
        if (globalVal != null && !globalVal.isBlank()) {
            return globalVal;
        }

        // 2. Contact attribute (if defaultValue is an attribute key)
        if (text.getDefaultValue() != null && !text.getDefaultValue().isBlank()) {
            String attrVal = ctx.getContactAttributes()
                    .getOrDefault(phone, new HashMap<>())
                    .get(text.getDefaultValue());
            if (attrVal != null && !attrVal.isBlank()) {
                return attrVal;
            }
        }

        // 3. Fallback (default or example)
        return ctx.getFallbackValues().getOrDefault(key, "");
    }

    // ==================== HELPERS ====================

    private List<TemplateTextDto> filterTexts(TemplateDto template, String type, Integer cardIdx) {
        if (template.getTexts() == null) return Collections.emptyList();
        return template.getTexts().stream()
                .filter(t -> type.equalsIgnoreCase(t.getType()))
                .filter(t -> matchesCard(t, cardIdx))
                .sorted((a, b) -> Integer.compare(
                        a.getTextIndex() != null ? a.getTextIndex() : 0,
                        b.getTextIndex() != null ? b.getTextIndex() : 0))
                .toList();
    }

    private boolean matchesCard(TemplateTextDto t, Integer cardIdx) {
        if (cardIdx == null) {
            return t.getIsCarousel() == null || !t.getIsCarousel();
        }
        return t.getIsCarousel() != null && t.getIsCarousel() && cardIdx.equals(t.getCardIndex());
    }

    private String compositeKey(String type, Integer textIdx, Integer cardIdx, Boolean isCarousel) {
        int card = (isCarousel != null && isCarousel && cardIdx != null) ? cardIdx : -1;
        int idx = textIdx != null ? textIdx : 0;
        return String.format("%s_%d_%d", type.toUpperCase(), idx, card);
    }

    private String resolveMediaUrl(TemplateComponentDto comp, SendTemplateNormalRequestDto request) {
        // Priority: request.mediaUrl > template example
        if (request.getMediaUrl() != null && !request.getMediaUrl().isBlank()) {
            return request.getMediaUrl();
        }
        if (comp.getExample() != null && comp.getExample().getHeaderHandle() != null
                && !comp.getExample().getHeaderHandle().isEmpty()) {
            return comp.getExample().getHeaderHandle().get(0);
        }
        return comp.getImageUrl();
    }

    private String extractCarouselMediaUrl(TemplateCarouselCardComponent comp) {
        if (comp.getExample() != null && comp.getExample().getHeaderHandle() != null
                && !comp.getExample().getHeaderHandle().isEmpty()) {
            return comp.getExample().getHeaderHandle().get(0);
        }
        return null;
    }

    private Parameter textParam(String text) {
        Parameter p = new Parameter();
        p.setType("text");
        p.setText(text);
        return p;
    }

    private Parameter mediaParam(MediaType type, String url) {
        Parameter p = new Parameter();
        p.setType(type.getValue().toLowerCase());

        Media media = switch (type) {
            case IMAGE -> new Image();
            case VIDEO -> new Video();
            case DOCUMENT -> new Document();
            default -> throw new InvalidMediaType("Unsupported: " + type);
        };
        media.setLink(url);

        switch (type) {
            case IMAGE -> p.setImage((Image) media);
            case VIDEO -> p.setVideo((Video) media);
            case DOCUMENT -> p.setDocument((Document) media);
        }
        return p;
    }

    private <T> void addIfNotNull(List<T> list, T elem) {
        if (elem != null) list.add(elem);
    }

    private <T> void addAll(List<T> list, List<T> elems) {
        if (elems != null) list.addAll(elems);
    }

    private <T> List<T> optList(T elem) {
        return elem != null ? List.of(elem) : Collections.emptyList();
    }

    // ==================== INNER CLASSES ====================

    @Data
    @Builder
    private static class NormalParameterContext {
        private Map<Integer, String> globalVariables;
        private Map<String, String> fallbackValues;
        private Map<Integer, CardParameters> carouselParameters;
        private Map<String, Map<String, String>> contactAttributes;
    }

    @Data
    @Builder
    private static class CardParameters {
        private Map<Integer, String> bodyVariables;
        private Map<Integer, Map<Integer, String>> buttonVariables;
        private String imageUrl;

        public static CardParameters empty() {
            return CardParameters.builder()
                    .bodyVariables(new HashMap<>())
                    .buttonVariables(new HashMap<>())
                    .build();
        }
    }
}