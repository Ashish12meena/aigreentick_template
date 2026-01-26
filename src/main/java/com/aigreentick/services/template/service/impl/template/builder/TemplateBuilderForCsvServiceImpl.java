package com.aigreentick.services.template.service.impl.template.builder;

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
import com.aigreentick.services.template.dto.request.template.csv.*;
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

@Service
@Slf4j
@RequiredArgsConstructor
public class TemplateBuilderForCsvServiceImpl {

    private final ChatContactServiceImpl chatContactService;

    private static final int MAX_CARDS = MessageConstants.MAX_CARDS;
    private static final int MAX_BUTTONS_PER_CARD = MessageConstants.MAX_BUTTONS_PER_CARD;

    public List<MessageRequest> buildSendableTemplatesFromCsv(
            Long userId,
            List<String> phoneNumbers,
            TemplateDto template,
            SendTemplateByCsvRequestDto csvRequest) {

        log.info("Building {} messages from CSV for template: {}", phoneNumbers.size(), template.getName());

        CsvParameterContext ctx = buildParameterContext(userId, phoneNumbers, template, csvRequest);

        return phoneNumbers.stream()
                .map(phone -> buildMessageRequest(phone, template, ctx, csvRequest))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ==================== CONTEXT BUILDING ====================

    private CsvParameterContext buildParameterContext(
            Long userId, List<String> phoneNumbers, TemplateDto template, SendTemplateByCsvRequestDto csvRequest) {

        Map<String, String> fallbacks = buildFallbackValues(template);
        Map<String, Map<Integer, String>> perContact = new HashMap<>();
        Map<Integer, String> global = new HashMap<>();
        parseCsvVariables(csvRequest.getVariables(), perContact, global);
        Map<Integer, CardParameters> carousel = parseCarouselParams(csvRequest.getCarouselCards());

        List<String> attrKeys = extractAttributeKeys(template);
        Map<String, Map<String, String>> contactAttrs = chatContactService.getContactAttributes(userId, phoneNumbers,
                attrKeys);

        return CsvParameterContext.builder()
                .fallbackValues(fallbacks)
                .perContactVariables(perContact)
                .globalVariables(global)
                .carouselParameters(carousel)
                .contactAttributes(contactAttrs)
                .build();
    }

    /**
     * Parse CSV variables with REINDEXING.
     * 
     * Key Feature: Ignores the incoming "variable" keys and reindexes based on
     * array position.
     * 
     * Example transformations:
     * - Input: [{"variable": 0, "value": "John"}, {"variable": 1, "value": "Doe"}]
     * Output: {1: "John", 2: "Doe"}
     * 
     * - Input: [{"variable": 5, "value": "Alice"}, {"variable": 10, "value":
     * "Smith"}]
     * Output: {1: "Alice", 2: "Smith"}
     * 
     * This ensures WhatsApp template variables {{1}}, {{2}}, etc. are always
     * correctly mapped
     * regardless of how the frontend/CSV sends the keys.
     */
    private void parseCsvVariables(List<VariableGroupDto> variables,
            Map<String, Map<Integer, String>> perContact, Map<Integer, String> global) {
        if (variables == null)
            return;

        for (VariableGroupDto group : variables) {
            Map<Integer, String> varMap = new HashMap<>();

            if (group.getVariable() != null) {
                List<VariableDto> sortedVars = new ArrayList<>(group.getVariable());

                // CRITICAL CHANGE: Reindex based on array position (1-based)
                for (int i = 0; i < sortedVars.size(); i++) {
                    VariableDto v = sortedVars.get(i);
                    if (v.getValue() != null) {
                        // Use array position + 1 as the key (1-based indexing for WhatsApp)
                        int reindexedKey = i + 1;
                        varMap.put(reindexedKey, v.getValue());

                        log.debug("Reindexed variable: original key={}, new key={}, value={}",
                                v.getVariable(), reindexedKey, v.getValue());
                    }
                }
            }

            if (group.getMobile() != null) {
                perContact.put(String.valueOf(group.getMobile()), varMap);
            } else {
                global.putAll(varMap);
            }
        }
    }

    private Map<Integer, CardParameters> parseCarouselParams(List<CarouselCardDto> cards) {
        Map<Integer, CardParameters> result = new HashMap<>();
        if (cards == null)
            return result;

        for (CarouselCardDto card : cards) {
            if (card.getCardIndex() == null)
                continue;

            result.put(card.getCardIndex(), CardParameters.builder()
                    .bodyVariables(parseStringKeysWithReindexing(card.getVariables()))
                    .buttonVariables(parseButtonVars(card.getButtons()))
                    .imageUrl(card.getImageUrl())
                    .build());
        }
        return result;
    }

    private Map<Integer, Map<Integer, String>> parseButtonVars(List<CarouselButtonDto> buttons) {
        Map<Integer, Map<Integer, String>> result = new HashMap<>();
        if (buttons == null)
            return result;

        for (int i = 0; i < buttons.size(); i++) {
            if (buttons.get(i).getVariables() != null) {
                result.put(i, parseStringKeysWithReindexing(buttons.get(i).getVariables()));
            }
        }
        return result;
    }

    /**
     * Parse string keys and REINDEX them based on sorted order.
     * 
     * Example:
     * - Input: {"5": "Alice", "10": "Smith"}
     * - Output: {1: "Alice", 2: "Smith"}
     * 
     * Steps:
     * 1. Sort entries by their numeric key
     * 2. Assign sequential indices starting from 1
     */
    private Map<Integer, String> parseStringKeysWithReindexing(Map<String, String> input) {
        Map<Integer, String> result = new HashMap<>();
        if (input == null || input.isEmpty())
            return result;

        try {
            // Convert to list of entries with numeric keys
            List<Map.Entry<Integer, String>> entries = new ArrayList<>();

            for (Map.Entry<String, String> entry : input.entrySet()) {
                try {
                    int key = Integer.parseInt(entry.getKey());
                    entries.add(Map.entry(key, entry.getValue()));
                } catch (NumberFormatException e) {
                    log.warn("Skipping non-numeric key in carousel variables: {}", entry.getKey());
                }
            }

            // Sort by original key (just for consistency, not strictly required)
            entries.sort(Map.Entry.comparingByKey());

            // Reindex starting from 1
            for (int i = 0; i < entries.size(); i++) {
                result.put(i + 1, entries.get(i).getValue());

                log.debug("Carousel variable reindex: original key={}, new key={}, value={}",
                        entries.get(i).getKey(), i + 1, entries.get(i).getValue());
            }

        } catch (Exception e) {
            log.error("Error parsing carousel variables with reindexing", e);
        }

        return result;
    }

    /**
     * Legacy method - kept for backward compatibility but updated to use reindexing
     * 
     * @deprecated Use parseStringKeysWithReindexing instead
     */
    @Deprecated
    private Map<Integer, String> parseStringKeys(Map<String, String> input) {
        return parseStringKeysWithReindexing(input);
    }

    /**
     * Build fallback values map with NEW field mapping.
     * Value: defaultValue (example value from Facebook)
     * 
     * Note: `text` field is now attribute key, not stored in fallbacks
     */
    private Map<String, String> buildFallbackValues(TemplateDto template) {
        Map<String, String> fallbacks = new HashMap<>();

        // Guard
        if (template.getTexts() == null || template.getTexts().isEmpty()) {
            return fallbacks;
        }

        for (TemplateTextDto t : template.getTexts()) {
            String key = compositeKey(t.getType(), t.getTextIndex(), t.getCardIndex(), t.getIsCarousel());

            // NEW: defaultValue is the example value from Facebook (primary fallback)
            // text field stores attribute name (not stored in fallbacks)
            String val = t.getDefaultValue();

            // If defaultValue is null/blank, use empty string
            if (val == null || val.isBlank()) {
                val = "";
            }

            fallbacks.put(key, val);
            log.debug("Fallback for {}: {}", key, val);
        }

        return fallbacks;
    }

    /**
     * Extract attribute keys from `text` field (NEW mapping).
     * 
     * OLD: defaultValue stored attribute keys
     * NEW: text field stores attribute keys
     */
    private List<String> extractAttributeKeys(TemplateDto template) {
        if (template.getTexts() == null || template.getTexts().isEmpty()) {
            return Collections.emptyList();
        }

        // NEW: Extract from `text` field (attribute names)
        return template.getTexts().stream()
                .map(TemplateTextDto::getText) // Changed from getDefaultValue()
                .filter(v -> v != null && !v.isBlank())
                .distinct()
                .toList();
    }

    // ==================== MESSAGE BUILDING ====================

    private MessageRequest buildMessageRequest(String phone, TemplateDto template,
            CsvParameterContext ctx, SendTemplateByCsvRequestDto csv) {

        MessageRequest req = new MessageRequest();
        req.setTo(phone);
        req.setType("template");

        SendableTemplate sendable = new SendableTemplate();
        sendable.setName(template.getName());
        sendable.setLanguage(new Language(template.getLanguage()));

        List<Component> components = new ArrayList<>();

        for (TemplateComponentDto comp : template.getComponents()) {
            switch (ComponentType.fromValue(comp.getType())) {
                case HEADER -> addIfNotNull(components, buildHeader(comp, template, phone, ctx, csv));
                case BODY -> addIfNotNull(components, buildBody(template, phone, ctx, null));
                case BUTTONS -> addAll(components, buildButtons(template, comp, phone, ctx, null));
                case CAROUSEL -> addIfNotNull(components, buildCarousel(comp, template, phone, ctx, csv));
                case LIMITED_TIME_OFFER -> {
                } // Not supported in CSV
                default -> throw new InvalidTemplateComponentType("Unsupported: " + comp.getType());
            }
        }

        sendable.setComponents(components.isEmpty() ? null : components);
        req.setTemplate(sendable);
        return req;
    }

    // ==================== HEADER ====================

    private Component buildHeader(TemplateComponentDto comp, TemplateDto template,
            String phone, CsvParameterContext ctx, SendTemplateByCsvRequestDto csv) {
        if ("TEXT".equalsIgnoreCase(comp.getFormat())) {
            return buildHeaderText(template, phone, ctx, null);
        }
        return buildHeaderMedia(comp);
    }

    private Component buildHeaderText(TemplateDto template, String phone, CsvParameterContext ctx, Integer cardIdx) {
        List<TemplateTextDto> texts = filterTexts(template, "HEADER", cardIdx);
        if (texts.isEmpty())
            return null;

        List<Parameter> params = texts.stream()
                .map(t -> resolveNonCarouselValue(t, phone, ctx))
                .filter(v -> !v.isEmpty())
                .map(v -> textParam(v))
                .collect(Collectors.toList());

        if (params.isEmpty())
            return null;

        Component c = new Component();
        c.setType("header");
        c.setParameters(params);
        return c;
    }

    private Component buildHeaderMedia(TemplateComponentDto comp) {
        String url = extractMediaUrl(comp);
        if (url == null)
            return null;

        MediaType type = MediaType.fromValue(comp.getFormat());
        Component c = new Component();
        c.setType("header");
        c.setParameters(List.of(mediaParam(type, url)));
        return c;
    }

    // ==================== BODY ====================

    private Component buildBody(TemplateDto template, String phone, CsvParameterContext ctx, Integer cardIdx) {
        List<TemplateTextDto> texts = filterTexts(template, "BODY", cardIdx);
        if (texts.isEmpty())
            return null;

        List<Parameter> params = texts.stream()
                .map(t -> textParam(resolveNonCarouselValue(t, phone, ctx)))
                .collect(Collectors.toList());

        if (params.isEmpty())
            return null;

        Component c = new Component();
        c.setType("body");
        c.setParameters(params);
        return c;
    }

    // ==================== BUTTONS ====================

    private List<Component> buildButtons(TemplateDto template, TemplateComponentDto comp,
            String phone, CsvParameterContext ctx, Integer cardIdx) {
        List<Component> result = new ArrayList<>();
        if (comp.getButtons() == null)
            return result;

        for (int i = 0; i < comp.getButtons().size(); i++) {
            TemplateComponentButtonDto btn = comp.getButtons().get(i);
            if (ButtonTypes.fromValue(btn.getType()) != ButtonTypes.URL)
                continue;

            List<TemplateTextDto> btnTexts = filterTexts(template, "BUTTON", cardIdx);
            if (btnTexts.isEmpty())
                continue;

            String val = resolveNonCarouselValue(btnTexts.get(0), phone, ctx);
            if (val.isEmpty())
                continue;

            Component c = new Component();
            c.setType("button");
            c.setSubType(btn.getType().toLowerCase());
            c.setIndex(String.valueOf(i));
            c.setParameters(List.of(textParam(val)));
            result.add(c);
        }
        return result;
    }

    // ==================== CAROUSEL ====================

    private Component buildCarousel(TemplateComponentDto comp, TemplateDto template,
            String phone, CsvParameterContext ctx, SendTemplateByCsvRequestDto csv) {
        if (comp.getCards() == null || comp.getCards().isEmpty()) {
            throw new CarouselConfigurationException("Carousel must have cards");
        }
        if (comp.getCards().size() > MAX_CARDS) {
            throw new CarouselConfigurationException("Max " + MAX_CARDS + " cards");
        }

        Component c = new Component();
        c.setType("carousel");
        c.setCards(comp.getCards().stream()
                .map(card -> buildCard(card, template, ctx))
                .collect(Collectors.toList()));
        return c;
    }

    private Card buildCard(TemplateComponentCardsDto templateCard, TemplateDto template, CsvParameterContext ctx) {
        Card card = new Card();
        Integer idx = templateCard.getIndex();
        card.setCardIndex(idx);

        CardParameters csvParams = ctx.getCarouselParameters().getOrDefault(idx, CardParameters.empty());

        List<CarouselComponent> comps = new ArrayList<>();
        for (TemplateCarouselCardComponent comp : templateCard.getComponents()) {
            comps.addAll(buildCardComponent(comp, template, idx, csvParams, ctx));
        }
        card.setComponents(comps);
        return card;
    }

    private List<CarouselComponent> buildCardComponent(TemplateCarouselCardComponent comp,
            TemplateDto template, Integer cardIdx, CardParameters csvParams, CsvParameterContext ctx) {

        return switch (ComponentType.fromValue(comp.getType())) {
            case HEADER -> optList(buildCarouselHeader(comp, cardIdx, csvParams));
            case BODY -> optList(buildCarouselBody(template, cardIdx, csvParams, ctx));
            case BUTTONS -> buildCarouselButtons(comp, cardIdx, csvParams);
            default -> throw new InvalidTemplateComponentType("Unsupported: " + comp.getType());
        };
    }

    private CarouselComponent buildCarouselHeader(TemplateCarouselCardComponent comp,
            Integer cardIdx, CardParameters csvParams) {
        String url = csvParams.getImageUrl();
        if (url == null || url.isBlank())
            url = extractCarouselMediaUrl(comp);
        if (url == null)
            return null;

        MediaType type = MediaType.fromValue(comp.getFormat());
        CarouselComponent c = new CarouselComponent();
        c.setType("header");
        c.setParameters(List.of(mediaParam(type, url)));
        return c;
    }

    private CarouselComponent buildCarouselBody(TemplateDto template, Integer cardIdx,
            CardParameters csvParams, CsvParameterContext ctx) {
        List<TemplateTextDto> texts = filterTexts(template, "BODY", cardIdx);
        if (texts.isEmpty())
            return null;

        List<Parameter> params = new ArrayList<>();
        for (TemplateTextDto t : texts) {
            int varIdx = t.getTextIndex() != null ? t.getTextIndex() : 0;

            // IMPORTANT: varIdx is 0-based from template, but we look up with 1-based key
            // because parseCarouselParams now uses 1-based indexing
            String val = csvParams.getBodyVariables().get(varIdx + 1);
            if (val == null || val.isBlank()) {
                val = ctx.getFallbackValues().getOrDefault(
                        compositeKey(t.getType(), t.getTextIndex(), cardIdx, true), "");
            }
            params.add(textParam(val));
        }

        if (params.isEmpty())
            return null;
        CarouselComponent c = new CarouselComponent();
        c.setType("body");
        c.setParameters(params);
        return c;
    }

    private List<CarouselComponent> buildCarouselButtons(TemplateCarouselCardComponent comp,
            Integer cardIdx, CardParameters csvParams) {
        List<TemplateCarouselButton> buttons = Optional.ofNullable(comp.getButtons()).orElse(Collections.emptyList());
        if (buttons.size() > MAX_BUTTONS_PER_CARD) {
            throw new CarouselConfigurationException("Max " + MAX_BUTTONS_PER_CARD + " buttons per card");
        }

        List<CarouselComponent> result = new ArrayList<>();
        for (int i = 0; i < buttons.size(); i++) {
            TemplateCarouselButton btn = buttons.get(i);
            ButtonTypes type = ButtonTypes.fromValue(btn.getType());

            if (type == ButtonTypes.URL) {
                Map<Integer, String> btnVars = csvParams.getButtonVariables().getOrDefault(i, new HashMap<>());

                // Look up with 1-based key (always 1 for URL buttons)
                String val = btnVars.get(1);
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
     * Resolve non-carousel variable value with NEW field mapping.
     * 
     * Resolution priority:
     * 1. Contact attribute (using `text` field as attribute key)
     * 2. Per-contact CSV variable (from request)
     * 3. Global CSV variable (from request)
     * 4. defaultValue (example value from Facebook - fallback)
     */
    private String resolveNonCarouselValue(TemplateTextDto text, String phone, CsvParameterContext ctx) {
        int varIdx = text.getTextIndex() != null ? text.getTextIndex() : 0;
        String key = compositeKey(text.getType(), text.getTextIndex(), text.getCardIndex(), text.getIsCarousel());

        // Priority 1: Contact attribute (NEW - using `text` field as attribute key)
        if (text.getText() != null && !text.getText().isBlank()) {
            String attrVal = ctx.getContactAttributes()
                    .getOrDefault(phone, new HashMap<>())
                    .get(text.getText()); // NEW: text = attribute name

            if (attrVal != null && !attrVal.isBlank()) {
                log.debug("Resolved contact attr for {}: {} = {}", phone, text.getText(), attrVal);
                return attrVal;
            }
        }

        // Priority 2: Per-contact CSV (1-based lookup)
        String perContact = ctx.getPerContactVariables()
                .getOrDefault(phone, new HashMap<>())
                .get(varIdx + 1);
        if (perContact != null && !perContact.isBlank()) {
            return perContact;
        }

        // Priority 3: Global CSV (1-based lookup)
        String global = ctx.getGlobalVariables().get(varIdx + 1);
        if (global != null && !global.isBlank()) {
            return global;
        }

        // Priority 4: Fallback to defaultValue (example value from Facebook)
        String fallback = ctx.getFallbackValues().getOrDefault(key, "");
        log.debug("Using fallback for {}: {}", key, fallback);
        return fallback;
    }

    // ==================== HELPERS ====================

    private List<TemplateTextDto> filterTexts(TemplateDto template, String type, Integer cardIdx) {
        if (template.getTexts() == null)
            return Collections.emptyList();
        return template.getTexts().stream()
                .filter(t -> type.equalsIgnoreCase(t.getType()))
                .filter(t -> matchesCard(t, cardIdx))
                .sorted((a, b) -> Integer.compare(
                        a.getTextIndex() != null ? a.getTextIndex() : 0,
                        b.getTextIndex() != null ? b.getTextIndex() : 0))
                .toList();
    }

    private boolean matchesCard(TemplateTextDto t, Integer cardIdx) {
        if (cardIdx == null)
            return t.getIsCarousel() == null || !t.getIsCarousel();
        return t.getIsCarousel() != null && t.getIsCarousel() && cardIdx.equals(t.getCardIndex());
    }

    private String compositeKey(String type, Integer textIdx, Integer cardIdx, Boolean isCarousel) {
        int card = (isCarousel != null && isCarousel && cardIdx != null) ? cardIdx : -1;
        int idx = textIdx != null ? textIdx : 0;
        return String.format("%s_%d_%d", type.toUpperCase(), idx, card);
    }

    private String extractMediaUrl(TemplateComponentDto comp) {
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
        if (elem != null)
            list.add(elem);
    }

    private <T> void addAll(List<T> list, List<T> elems) {
        if (elems != null)
            list.addAll(elems);
    }

    private <T> List<T> optList(T elem) {
        return elem != null ? List.of(elem) : Collections.emptyList();
    }

    // ==================== INNER CLASSES ====================

    @Data
    @Builder
    private static class CsvParameterContext {
        private Map<String, String> fallbackValues;
        private Map<String, Map<Integer, String>> perContactVariables;
        private Map<Integer, String> globalVariables;
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