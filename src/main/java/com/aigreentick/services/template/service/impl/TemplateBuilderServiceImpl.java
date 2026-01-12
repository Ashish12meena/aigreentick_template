package com.aigreentick.services.template.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.aigreentick.services.template.constants.MessageConstants;
import com.aigreentick.services.template.dto.build.Card;
import com.aigreentick.services.template.dto.build.CarouselComponent;
import com.aigreentick.services.template.dto.build.Component;
import com.aigreentick.services.template.dto.build.Document;
import com.aigreentick.services.template.dto.build.Image;
import com.aigreentick.services.template.dto.build.Language;
import com.aigreentick.services.template.dto.build.LimitedTimeOffer;
import com.aigreentick.services.template.dto.build.Media;
import com.aigreentick.services.template.dto.build.MessageRequest;
import com.aigreentick.services.template.dto.build.Parameter;
import com.aigreentick.services.template.dto.build.Product;
import com.aigreentick.services.template.dto.build.SendableTemplate;
import com.aigreentick.services.template.dto.build.TemplateCarouselButton;
import com.aigreentick.services.template.dto.build.TemplateCarouselCardComponent;
import com.aigreentick.services.template.dto.build.TemplateComponentButtonDto;
import com.aigreentick.services.template.dto.build.TemplateComponentCardsDto;
import com.aigreentick.services.template.dto.build.TemplateComponentDto;
import com.aigreentick.services.template.dto.build.TemplateDto;
import com.aigreentick.services.template.dto.build.TemplateTextDto;
import com.aigreentick.services.template.dto.build.TextParameter;
import com.aigreentick.services.template.dto.build.Video;
import com.aigreentick.services.template.dto.request.SendTemplateRequestDto;
import com.aigreentick.services.template.enums.ButtonTypes;
import com.aigreentick.services.template.enums.ComponentType;
import com.aigreentick.services.template.enums.MediaType;
import com.aigreentick.services.template.enums.TemplateCategory;
import com.aigreentick.services.template.exceptions.CarouselConfigurationException;
import com.aigreentick.services.template.exceptions.InvalidMediaType;
import com.aigreentick.services.template.exceptions.InvalidTemplateCategory;
import com.aigreentick.services.template.exceptions.InvalidTemplateComponentType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class TemplateBuilderServiceImpl {
    private final ChatContactServiceImpl chatContactService;
    private final MediaServiceImpl mediaService;

    private static final int MAX_CARDS = MessageConstants.MAX_CARDS;
    private static final int MAX_BUTTONS_PER_CARD = MessageConstants.MAX_BUTTONS_PER_CARD;

    public List<MessageRequest> buildSendableTemplates(
            Long userId,
            List<String> phoneNumbers,
            TemplateDto template,
            SendTemplateRequestDto requestDto) {

        TemplateCategory templateCategory = TemplateCategory.valueOf(template.getCategory());

        return switch (templateCategory) {
            case AUTHENTICATION -> buildAuthenticationSendTemplates(phoneNumbers, template, requestDto);
            case MARKETING, UTILITY -> {
                // Build resolved parameters map for each phone number
                Map<String, Map<String, String>> resolvedParams = resolveAllParameters(
                        userId, phoneNumbers, template, requestDto);
                yield buildMarketingSendTemplates(phoneNumbers, template, resolvedParams, requestDto);
            }
            default -> throw new InvalidTemplateCategory("Unsupported template category: " + templateCategory);
        };
    }

    // ==================== PARAMETER RESOLUTION ====================

    /**
     * Resolves parameters for all phone numbers.
     * Creates a composite key for each variable: type_textIndex_cardIndex
     * Value priority: Contact Attribute > User Default > Example Value (fallback)
     */
    private Map<String, Map<String, String>> resolveAllParameters(
            Long userId,
            List<String> phoneNumbers,
            TemplateDto template,
            SendTemplateRequestDto requestDto) {

        // Build fallback map: compositeKey -> (defaultValue or exampleValue)
        Map<String, String> fallbackValues = buildFallbackValuesMap(template);

        if (requestDto.isFullyPrameterized()) {
            log.info("Using fallback values for fully parameterized template");
            return buildParametersFromFallbacks(phoneNumbers, fallbackValues);
        }

        // Get contact attributes for personalization
        List<String> attributeKeys = extractAttributeKeys(template);
        Map<String, Map<String, String>> contactAttributes = chatContactService
                .getContactAttributes(userId, phoneNumbers, attributeKeys);

        return buildParametersWithFallback(phoneNumbers, template, contactAttributes, fallbackValues);
    }

    /**
     * Build fallback values map with composite key.
     * Key format: TYPE_textIndex_cardIndex (cardIndex is -1 for non-carousel)
     * Value: defaultValue if set, otherwise text (example value)
     */
    private Map<String, String> buildFallbackValuesMap(TemplateDto template) {
        Map<String, String> fallbacks = new HashMap<>();

        // Guard
        if (template.getTexts() == null || template.getTexts().isEmpty()) {
            return fallbacks;
        }

        for (TemplateTextDto textDto : template.getTexts()) {
            String compositeKey = buildCompositeKey(
                    textDto.getType(),
                    textDto.getTextIndex(),
                    textDto.getCardIndex(),
                    textDto.getIsCarousel());

            // Priority: defaultValue > text (example value) > empty string
            String value = textDto.getDefaultValue();
            if (value == null || value.isBlank()) {
                value = textDto.getText(); // Example value as fallback
            }
            if (value == null) {
                value = "";
            }

            fallbacks.put(compositeKey, value);
            log.debug("Fallback for {}: {}", compositeKey, value);
        }

        return fallbacks;
    }

    /**
     * Build composite key for variable lookup
     */
    private String buildCompositeKey(String type, Integer textIndex, Integer cardIndex, Boolean isCarousel) {
        int card = (isCarousel != null && isCarousel && cardIndex != null) ? cardIndex : -1;
        int index = textIndex != null ? textIndex : 0;
        return String.format("%s_%d_%d", type.toUpperCase(), index, card);
    }

    /**
     * Extract attribute keys that might be stored in defaultValue field
     * for contact attribute mapping
     */
    private List<String> extractAttributeKeys(TemplateDto template) {
        if (template.getTexts() == null || template.getTexts().isEmpty()) {
            return Collections.emptyList();
        }

        return template.getTexts().stream()
                .map(TemplateTextDto::getDefaultValue)
                .filter(Objects::nonNull)
                .filter(val -> !val.isBlank())
                .distinct()
                .toList();
    }

    /**
     * Build parameters using only fallback values (for fully parameterized
     * templates)
     */
    private Map<String, Map<String, String>> buildParametersFromFallbacks(
            List<String> phoneNumbers,
            Map<String, String> fallbackValues) {

        Map<String, Map<String, String>> result = new HashMap<>();
        for (String phoneNumber : phoneNumbers) {
            result.put(phoneNumber, new HashMap<>(fallbackValues));
        }
        return result;
    }

    /**
     * Build parameters with contact attributes and fallback support
     */
    private Map<String, Map<String, String>> buildParametersWithFallback(
            List<String> phoneNumbers,
            TemplateDto template,
            Map<String, Map<String, String>> contactAttributes,
            Map<String, String> fallbackValues) {

        Map<String, Map<String, String>> result = new HashMap<>();

        for (String phoneNumber : phoneNumbers) {
            Map<String, String> phoneParams = new HashMap<>();
            Map<String, String> contactAttrs = contactAttributes.getOrDefault(phoneNumber, new HashMap<>());

            for (TemplateTextDto textDto : template.getTexts()) {
                String compositeKey = buildCompositeKey(
                        textDto.getType(),
                        textDto.getTextIndex(),
                        textDto.getCardIndex(),
                        textDto.getIsCarousel());

                // Try to get value from contact attributes using defaultValue as key
                String value = null;
                if (textDto.getDefaultValue() != null && !textDto.getDefaultValue().isBlank()) {
                    value = contactAttrs.get(textDto.getDefaultValue());
                }

                // Fallback to stored value if contact attribute not found
                if (value == null || value.isBlank()) {
                    value = fallbackValues.getOrDefault(compositeKey, "");
                }

                phoneParams.put(compositeKey, value);
            }

            result.put(phoneNumber, phoneParams);
        }

        log.debug("Built parameters for {} phone numbers", phoneNumbers.size());
        return result;
    }

    // ==================== AUTHENTICATION TEMPLATES ====================

    private List<MessageRequest> buildAuthenticationSendTemplates(
            List<String> phoneNumbers,
            TemplateDto template,
            SendTemplateRequestDto requestDto) {
        return phoneNumbers.stream()
                .map(number -> buildAuthenticationSendTemplate(number, template, requestDto))
                .collect(Collectors.toList());
    }

    public MessageRequest buildAuthenticationSendTemplate(
            String phoneNumber,
            TemplateDto template,
            SendTemplateRequestDto requestDto) {

        MessageRequest messageRequest = new MessageRequest();
        messageRequest.setTo(phoneNumber);

        SendableTemplate sendableTemplate = new SendableTemplate();
        sendableTemplate.setName(template.getName());
        sendableTemplate.setLanguage(new Language(requestDto.getLanguageCode()));

        List<Component> components = new ArrayList<>();

        Component bodyComponent = new Component();
        bodyComponent.setType(ComponentType.BODY.getValue().toLowerCase());
        Parameter parameter = new Parameter();
        parameter.setType("text");
        parameter.setText(requestDto.getOtp());
        bodyComponent.setParameters(List.of(parameter));
        components.add(bodyComponent);

        Component buttonComponent = new Component();
        buttonComponent.setType("button");
        buttonComponent.setSubType(ButtonTypes.URL.getValue().toLowerCase());
        buttonComponent.setIndex("0");

        Parameter buttonParameter = new Parameter();
        buttonParameter.setType("text");
        buttonParameter.setText(requestDto.getOtp());
        buttonComponent.setParameters(List.of(buttonParameter));
        components.add(buttonComponent);

        sendableTemplate.setComponents(components);
        messageRequest.setTemplate(sendableTemplate);

        return messageRequest;
    }

    // ==================== MARKETING/UTILITY TEMPLATES ====================

    private List<MessageRequest> buildMarketingSendTemplates(
            List<String> phoneNumbers,
            TemplateDto template,
            Map<String, Map<String, String>> resolvedParams,
            SendTemplateRequestDto requestDto) {

        return phoneNumbers.stream()
                .map(phoneNumber -> buildMarketingSendTemplate(
                        phoneNumber, template, resolvedParams.get(phoneNumber), requestDto))
                .toList();
    }

    public MessageRequest buildMarketingSendTemplate(
            String phoneNumber,
            TemplateDto template,
            Map<String, String> resolvedParams,
            SendTemplateRequestDto requestDto) {

        MessageRequest messageRequest = new MessageRequest();
        messageRequest.setTo(phoneNumber);
        messageRequest.setType("template");

        SendableTemplate sendableTemplate = new SendableTemplate();
        sendableTemplate.setName(template.getName());
        sendableTemplate.setLanguage(new Language(template.getLanguage()));

        List<Component> components = new ArrayList<>();

        for (TemplateComponentDto comp : template.getComponents()) {
            switch (ComponentType.fromValue(comp.getType())) {
                case HEADER -> {
                    Component header = buildHeaderComponent(comp, template, resolvedParams, requestDto);
                    addIfNotNull(components, header);
                }
                case BODY -> {
                    Component body = buildBodyComponent(template, resolvedParams, null); // null cardIndex for
                                                                                         // non-carousel
                    addIfNotNull(components, body);
                }
                case BUTTONS -> {
                    List<Component> buttonComponents = buildButtonComponents(template, comp, resolvedParams, null);
                    addAllIfNotEmpty(components, buttonComponents);
                }
                case LIMITED_TIME_OFFER -> {
                    Component lto = buildLimitedTimeOfferComponent(requestDto);
                    addIfNotNull(components, lto);
                }
                case CAROUSEL -> {
                    Component carousel = buildCarouselComponent(comp, template, resolvedParams, requestDto);
                    addIfNotNull(components, carousel);
                }
                default -> throw new InvalidTemplateComponentType("Unsupported component type: " + comp.getType());
            }
        }

        addCopyCodeButtonIfPresent(requestDto, template, components);

        sendableTemplate.setComponents(components.isEmpty() ? null : components);
        messageRequest.setTemplate(sendableTemplate);

        return messageRequest;
    }

    // ==================== HEADER COMPONENT ====================

    private Component buildHeaderComponent(
            TemplateComponentDto comp,
            TemplateDto template,
            Map<String, String> resolvedParams,
            SendTemplateRequestDto requestDto) {

        if ("TEXT".equalsIgnoreCase(comp.getFormat())) {
            return buildHeaderTextComponent(template, resolvedParams, null);
        } else {
            return buildHeaderMediaComponent(comp, requestDto);
        }
    }

    /**
     * Build HEADER text component using resolved parameters
     */
    private Component buildHeaderTextComponent(
            TemplateDto template,
            Map<String, String> resolvedParams,
            Integer cardIndex) {

        //  Guard
        if (template.getTexts() == null || template.getTexts().isEmpty()) {
            return null;
        }

        List<TemplateTextDto> headerTexts = template.getTexts().stream()
                .filter(t -> "HEADER".equalsIgnoreCase(t.getType()))
                .filter(t -> matchesCardIndex(t, cardIndex))
                .sorted((a, b) -> Integer.compare(
                        a.getTextIndex() != null ? a.getTextIndex() : 0,
                        b.getTextIndex() != null ? b.getTextIndex() : 0))
                .toList();

        if (headerTexts.isEmpty())
            return null;

        List<Parameter> params = new ArrayList<>();
        for (TemplateTextDto textDto : headerTexts) {
            String compositeKey = buildCompositeKey(
                    textDto.getType(),
                    textDto.getTextIndex(),
                    cardIndex,
                    textDto.getIsCarousel());
            String value = resolveValueWithFallback(textDto, resolvedParams, compositeKey);
            if (!value.isEmpty()) {
                params.add(buildTextParameter(value, Parameter::new));
            }
        }

        if (params.isEmpty())
            return null;

        Component component = new Component();
        component.setType(ComponentType.HEADER.getValue().toLowerCase());
        component.setParameters(params);
        return component;
    }

    private Component buildHeaderMediaComponent(
            TemplateComponentDto comp,
            SendTemplateRequestDto requestDto) {

        MediaType mediaType = MediaType.fromValue(comp.getFormat());

        String mediaId = resolveMediaId(requestDto);
        String mediaUrl = resolveMediaUrl(comp, requestDto);

        if (mediaId == null && mediaUrl == null) {
            log.warn("No media ID or URL available for header component");
            return null;
        }

        Component component = new Component();
        component.setType(ComponentType.HEADER.getValue().toLowerCase());

        Parameter mediaParam = buildMediaParameterWithFallback(mediaType, mediaId, mediaUrl);
        component.setParameters(List.of(mediaParam));

        return component;
    }

    // ==================== BODY COMPONENT ====================

    /**
     * Build BODY component using resolved parameters with cardIndex support
     */
    private Component buildBodyComponent(
            TemplateDto template,
            Map<String, String> resolvedParams,
            Integer cardIndex) {

        // Guard
        if (template.getTexts() == null || template.getTexts().isEmpty()) {
            return null;
        }

        List<TemplateTextDto> bodyTexts = template.getTexts().stream()
                .filter(t -> "BODY".equalsIgnoreCase(t.getType()))
                .filter(t -> matchesCardIndex(t, cardIndex))
                .sorted((a, b) -> Integer.compare(
                        a.getTextIndex() != null ? a.getTextIndex() : 0,
                        b.getTextIndex() != null ? b.getTextIndex() : 0))
                .toList();

        if (bodyTexts.isEmpty())
            return null;

        List<Parameter> params = new ArrayList<>();
        for (TemplateTextDto textDto : bodyTexts) {
            String compositeKey = buildCompositeKey(
                    textDto.getType(),
                    textDto.getTextIndex(),
                    cardIndex,
                    textDto.getIsCarousel());
            String value = resolveValueWithFallback(textDto, resolvedParams, compositeKey);
            params.add(buildTextParameter(value, Parameter::new));
        }

        if (params.isEmpty())
            return null;

        Component component = new Component();
        component.setType(ComponentType.BODY.getValue().toLowerCase());
        component.setParameters(params);
        return component;
    }

    // ==================== BUTTON COMPONENT ====================

    /**
     * Build BUTTON components using resolved parameters with cardIndex support
     * For non-carousel templates
     */
    private List<Component> buildButtonComponents(
            TemplateDto template,
            TemplateComponentDto templateComponent,
            Map<String, String> resolvedParams,
            Integer cardIndex) {
        //  Guard
        if (template.getTexts() == null || template.getTexts().isEmpty()) {
            return null;
        }

        List<Component> buttonComponents = new ArrayList<>();

        List<TemplateComponentButtonDto> buttons = templateComponent.getButtons();

        for (int buttonPosition = 0; buttonPosition < buttons.size(); buttonPosition++) {
            TemplateComponentButtonDto button = buttons.get(buttonPosition);

            if (ButtonTypes.fromValue(button.getType()) == ButtonTypes.URL) {
                // Find matching BUTTON text for this button
                List<TemplateTextDto> buttonTexts = template.getTexts().stream()
                        .filter(t -> "BUTTON".equalsIgnoreCase(t.getType()))
                        .filter(t -> matchesCardIndex(t, cardIndex))
                        .toList();

                if (!buttonTexts.isEmpty()) {
                    TemplateTextDto textDto = buttonTexts.get(0);
                    String compositeKey = buildCompositeKey(
                            textDto.getType(),
                            textDto.getTextIndex(),
                            cardIndex,
                            textDto.getIsCarousel());

                    String value = resolveValueWithFallback(textDto, resolvedParams, compositeKey);

                    if (!value.isEmpty()) {
                        Parameter param = buildTextParameter(value, Parameter::new);
                        Component component = new Component();
                        component.setType("button");
                        component.setSubType(button.getType().toLowerCase());
                        component.setIndex(String.valueOf(buttonPosition));
                        component.setParameters(List.of(param));
                        buttonComponents.add(component);
                    }
                }
            }
        }

        return buttonComponents.isEmpty() ? null : buttonComponents;
    }

    private void addCopyCodeButtonIfPresent(
            SendTemplateRequestDto requestDto,
            TemplateDto template,
            List<Component> components) {

        if (requestDto.getCopyCode() == null)
            return;

        template.getComponents().stream()
                .filter(c -> ComponentType.BUTTONS.getValue().equalsIgnoreCase(c.getType()))
                .flatMap(c -> c.getButtons().stream())
                .filter(b -> ButtonTypes.COPY_CODE.getValue().equalsIgnoreCase(b.getType()))
                .findFirst()
                .ifPresent(button -> {
                    Component couponButton = new Component();
                    couponButton.setType("button");
                    couponButton.setSubType(button.getType().toLowerCase());
                    couponButton.setIndex(String.valueOf(button.getIndex()));

                    Parameter param = new Parameter();
                    param.setType("coupon_code");
                    param.setCouponCode(requestDto.getCopyCode());
                    couponButton.setParameters(List.of(param));

                    components.add(couponButton);
                });
    }

    // ==================== LIMITED TIME OFFER ====================

    private Component buildLimitedTimeOfferComponent(SendTemplateRequestDto requestDto) {
        if (requestDto.getExpirationTimeMs() == null) {
            throw new IllegalArgumentException("expiration_time_ms required for LTO templates");
        }

        Component component = new Component();
        component.setType(ComponentType.LIMITED_TIME_OFFER.getValue().toLowerCase());
        component.setParameters(List.of(buildLimitedTimeOfferParam(requestDto.getExpirationTimeMs())));
        return component;
    }

    // ==================== CAROUSEL COMPONENT ====================

    private Component buildCarouselComponent(
            TemplateComponentDto comp,
            TemplateDto template,
            Map<String, String> resolvedParams,
            SendTemplateRequestDto requestDto) {

        if (comp.getCards() == null || comp.getCards().isEmpty()) {
            throw new CarouselConfigurationException("Carousel component must contain at least one card");
        }
        if (comp.getCards().size() > MAX_CARDS) {
            throw new CarouselConfigurationException("Carousel supports max " + MAX_CARDS + " cards");
        }

        Queue<String> mediaIdQueue = new ConcurrentLinkedQueue<>(
                Optional.ofNullable(requestDto.getMediaIdsForCarosel()).orElse(Collections.emptyList()));

        Queue<String> productRetailerIds = new ConcurrentLinkedQueue<>(
                Optional.ofNullable(requestDto.getProductRetailerIds()).orElse(Collections.emptyList()));

        Component component = new Component();
        component.setType(comp.getType().toLowerCase());

        List<Card> cards = comp.getCards().stream()
                .map(templateCard -> buildCard(
                        templateCard, template, resolvedParams, mediaIdQueue, productRetailerIds, requestDto))
                .toList();

        component.setCards(cards);
        return component;
    }

    /**
     * Build a single carousel card with proper cardIndex parameter resolution
     */
    private Card buildCard(
            TemplateComponentCardsDto templateCard,
            TemplateDto template,
            Map<String, String> resolvedParams,
            Queue<String> mediaIdQueue,
            Queue<String> productQueue,
            SendTemplateRequestDto requestDto) {

        Card card = new Card();
        Integer cardIndex = templateCard.getIndex();
        card.setCardIndex(cardIndex);

        List<CarouselComponent> carouselComponents = new ArrayList<>();

        for (TemplateCarouselCardComponent comp : templateCard.getComponents()) {
            List<CarouselComponent> builtComponents = buildCarouselCardComponent(
                    comp, template, resolvedParams, cardIndex, mediaIdQueue, productQueue, requestDto);
            addAllIfNotEmpty(carouselComponents, builtComponents);
        }

        card.setComponents(carouselComponents);
        return card;
    }

    /**
     * Build carousel card component with cardIndex for parameter lookup
     */
    private List<CarouselComponent> buildCarouselCardComponent(
            TemplateCarouselCardComponent comp,
            TemplateDto template,
            Map<String, String> resolvedParams,
            Integer cardIndex,
            Queue<String> mediaIdQueue,
            Queue<String> productQueue,
            SendTemplateRequestDto requestDto) {

        return switch (ComponentType.fromValue(comp.getType())) {
            case HEADER -> {
                MediaType mediaType = MediaType.fromValue(comp.getFormat());
                if (mediaType == MediaType.PRODUCT) {
                    yield List.of(buildCarouselHeaderProductComponent(comp, requestDto, productQueue.poll()));
                } else if (mediaType == MediaType.IMAGE || mediaType == MediaType.VIDEO
                        || mediaType == MediaType.DOCUMENT) {
                    yield List.of(buildCarouselHeaderMediaComponent(comp, mediaIdQueue.poll()));
                } else {
                    // TEXT header for carousel
                    CarouselComponent header = buildCarouselHeaderTextComponent(template, resolvedParams, cardIndex);
                    yield header != null ? List.of(header) : Collections.emptyList();
                }
            }
            case BODY -> {
                CarouselComponent body = buildCarouselBodyComponent(template, resolvedParams, cardIndex);
                yield body != null ? List.of(body) : Collections.emptyList();
            }
            case BUTTONS -> buildCarouselButtonComponents(template, comp, resolvedParams, cardIndex);
            default -> throw new InvalidTemplateComponentType("Unsupported carousel component type: " + comp.getType());
        };
    }

    /**
     * Build carousel HEADER text component with cardIndex
     */
    private CarouselComponent buildCarouselHeaderTextComponent(
            TemplateDto template,
            Map<String, String> resolvedParams,
            Integer cardIndex) {

        //  Guard
        if (template.getTexts() == null || template.getTexts().isEmpty()) {
            return null;
        }

        List<TemplateTextDto> headerTexts = template.getTexts().stream()
                .filter(t -> "HEADER".equalsIgnoreCase(t.getType()))
                .filter(t -> matchesCardIndex(t, cardIndex))
                .sorted((a, b) -> Integer.compare(
                        a.getTextIndex() != null ? a.getTextIndex() : 0,
                        b.getTextIndex() != null ? b.getTextIndex() : 0))
                .toList();

        if (headerTexts.isEmpty())
            return null;

        List<Parameter> params = new ArrayList<>();
        for (TemplateTextDto textDto : headerTexts) {
            String compositeKey = buildCompositeKey(
                    textDto.getType(),
                    textDto.getTextIndex(),
                    cardIndex,
                    true);
            String value = resolveValueWithFallback(textDto, resolvedParams, compositeKey);
            if (!value.isEmpty()) {
                params.add(buildTextParameter(value, Parameter::new));
            }
        }

        if (params.isEmpty())
            return null;

        CarouselComponent component = new CarouselComponent();
        component.setType(ComponentType.HEADER.getValue().toLowerCase());
        component.setParameters(params);
        return component;
    }

    private CarouselComponent buildCarouselHeaderMediaComponent(
            TemplateCarouselCardComponent comp,
            String mediaIdFromQueue) {

        MediaType mediaType = MediaType.fromValue(comp.getFormat());
        String templateUrl = extractTemplateMediaUrl(comp);
        Parameter mediaParam = buildMediaParameterWithFallback(mediaType, mediaIdFromQueue, templateUrl);

        if (mediaParam == null) {
            throw new CarouselConfigurationException(
                    "No media ID or URL available for carousel card header.");
        }

        CarouselComponent headerComponent = new CarouselComponent();
        headerComponent.setType(ComponentType.HEADER.getValue().toLowerCase());
        headerComponent.setParameters(List.of(mediaParam));

        return headerComponent;
    }

    private CarouselComponent buildCarouselHeaderProductComponent(
            TemplateCarouselCardComponent comp,
            SendTemplateRequestDto requestDto,
            String productRetailerId) {

        CarouselComponent headerComponent = new CarouselComponent();
        headerComponent.setType(ComponentType.HEADER.getValue().toLowerCase());

        Parameter productParam = buildProductParameter(requestDto.getCatalogId(), productRetailerId);
        headerComponent.setParameters(List.of(productParam));

        return headerComponent;
    }

    /**
     * Build carousel BODY component with cardIndex for parameter lookup
     */
    private CarouselComponent buildCarouselBodyComponent(
            TemplateDto template,
            Map<String, String> resolvedParams,
            Integer cardIndex) {

        //  Guard
        if (template.getTexts() == null || template.getTexts().isEmpty()) {
            return null;
        }

        List<TemplateTextDto> bodyTexts = template.getTexts().stream()
                .filter(t -> "BODY".equalsIgnoreCase(t.getType()))
                .filter(t -> matchesCardIndex(t, cardIndex))
                .sorted((a, b) -> Integer.compare(
                        a.getTextIndex() != null ? a.getTextIndex() : 0,
                        b.getTextIndex() != null ? b.getTextIndex() : 0))
                .toList();

        if (bodyTexts.isEmpty())
            return null;

        List<Parameter> params = new ArrayList<>();
        for (TemplateTextDto textDto : bodyTexts) {
            String compositeKey = buildCompositeKey(
                    textDto.getType(),
                    textDto.getTextIndex(),
                    cardIndex,
                    true);
            String value = resolveValueWithFallback(textDto, resolvedParams, compositeKey);
            params.add(buildTextParameter(value, Parameter::new));
        }

        if (params.isEmpty())
            return null;

        CarouselComponent component = new CarouselComponent();
        component.setType(ComponentType.BODY.getValue().toLowerCase());
        component.setParameters(params);
        return component;
    }

    /**
     * Build carousel BUTTON components with cardIndex for parameter lookup
     * Uses button position in the card's button list as the index
     */
    private List<CarouselComponent> buildCarouselButtonComponents(
            TemplateDto template,
            TemplateCarouselCardComponent comp,
            Map<String, String> resolvedParams,
            Integer cardIndex) {
        //  Guard
        if (template.getTexts() == null || template.getTexts().isEmpty()) {
            return null;
        }

        List<TemplateCarouselButton> buttons = Optional.ofNullable(comp.getButtons())
                .orElse(Collections.emptyList());

        if (buttons.size() > MAX_BUTTONS_PER_CARD) {
            throw new CarouselConfigurationException(
                    "Carousel card supports at most " + MAX_BUTTONS_PER_CARD + " buttons");
        }

        String payload = "payload";
        List<CarouselComponent> buttonComponents = new ArrayList<>();

        // Iterate with index to get button position in card
        for (int buttonPositionInCard = 0; buttonPositionInCard < buttons.size(); buttonPositionInCard++) {
            TemplateCarouselButton button = buttons.get(buttonPositionInCard);

            CarouselComponent btnComp = switch (ButtonTypes.fromValue(button.getType())) {
                case URL -> buildCarouselUrlButton(template, button, resolvedParams, cardIndex, buttonPositionInCard);
                case QUICK_REPLY -> buildCarouselQuickReplyButton(button, payload, buttonPositionInCard);
                default -> null;
            };
            addIfNotNull(buttonComponents, btnComp);
        }

        return buttonComponents;
    }

    /**
     * Build carousel URL button with cardIndex for parameter lookup
     * Uses buttonPositionInCard as the index in the output
     * Matches TemplateText by cardIndex to get the URL variable value
     */
    private CarouselComponent buildCarouselUrlButton(
            TemplateDto template,
            TemplateCarouselButton button,
            Map<String, String> resolvedParams,
            Integer cardIndex,
            int buttonPositionInCard) {

        // Find BUTTON text for this cardIndex
        // URL buttons have variables like {{1}} in the URL
        // TemplateText stores the example/default value for each card's URL button
        List<TemplateTextDto> buttonTexts = template.getTexts().stream()
                .filter(t -> "BUTTON".equalsIgnoreCase(t.getType()))
                .filter(t -> matchesCardIndex(t, cardIndex))
                .toList();

        if (buttonTexts.isEmpty()) {
            log.debug("No BUTTON text found for card {}", cardIndex);
            return null;
        }

        // Get the first matching button text for this card
        // (each card typically has one URL button with one variable)
        TemplateTextDto textDto = buttonTexts.get(0);

        // Build composite key for resolved params lookup
        String compositeKey = buildCompositeKey(
                textDto.getType(),
                textDto.getTextIndex(),
                cardIndex,
                true);

        // Priority: resolvedParams (contact attr/default) > text (example value)
        String value = resolvedParams.get(compositeKey);
        if (value == null || value.isBlank()) {
            // Fallback to defaultValue
            value = textDto.getDefaultValue();
        }
        if (value == null || value.isBlank()) {
            // Fallback to text (example value from Facebook)
            value = textDto.getText();
        }
        if (value == null || value.isBlank()) {
            log.debug("No value found for URL button at card {} position {}", cardIndex, buttonPositionInCard);
            return null;
        }

        Parameter param = buildTextParameter(value, Parameter::new);

        CarouselComponent component = new CarouselComponent();
        component.setType("button");
        component.setSubType(button.getType().toLowerCase());
        component.setIndex(buttonPositionInCard); // Use position in card's button list
        component.setParameters(List.of(param));

        log.debug("Built URL button for card {} at index {} with value: {}", cardIndex, buttonPositionInCard, value);
        return component;
    }

    /**
     * Build carousel Quick Reply button with index
     */
    private CarouselComponent buildCarouselQuickReplyButton(
            TemplateCarouselButton button,
            String payload,
            int buttonPositionInCard) {

        Parameter param = new Parameter();
        param.setType("payload");
        param.setPayload(payload);

        CarouselComponent component = new CarouselComponent();
        component.setType("button");
        component.setSubType(button.getType().toLowerCase());
        component.setIndex(buttonPositionInCard); // Use position in card's button list
        component.setParameters(List.of(param));
        return component;
    }

    // ==================== HELPER METHODS ====================

    /**
     * Check if TemplateTextDto matches the given cardIndex
     * For non-carousel: cardIndex is null, match texts where isCarousel is
     * false/null
     * For carousel: match texts where cardIndex matches
     */
    private boolean matchesCardIndex(TemplateTextDto textDto, Integer cardIndex) {
        if (cardIndex == null) {
            // Non-carousel component - match texts that are NOT carousel
            return textDto.getIsCarousel() == null || !textDto.getIsCarousel();
        } else {
            // Carousel component - match texts with same cardIndex
            return textDto.getIsCarousel() != null
                    && textDto.getIsCarousel()
                    && cardIndex.equals(textDto.getCardIndex());
        }
    }

    /**
     * Resolve value with priority: defaultValue > text (example value)
     */
    private String resolveValueWithFallback(TemplateTextDto textDto, Map<String, String> resolvedParams,
            String compositeKey) {
        // First try resolved params (from contact attributes or pre-resolved)
        String value = resolvedParams.get(compositeKey);

        // Fallback to defaultValue (user configured)
        if (value == null || value.isBlank()) {
            value = textDto.getDefaultValue();
        }

        // Fallback to text (example value from Facebook)
        if (value == null || value.isBlank()) {
            value = textDto.getText();
        }

        return value != null ? value : "";
    }

    private String resolveMediaId(SendTemplateRequestDto requestDto) {
        if (Boolean.TRUE.equals(requestDto.getIsMedia()) && requestDto.getMediaId() > 0) {
            return mediaService.getMediaIdById(requestDto.getMediaId());
        }
        return null;
    }

    private String resolveMediaUrl(TemplateComponentDto comp, SendTemplateRequestDto requestDto) {
        if (requestDto.getMediaUrl() != null && !requestDto.getMediaUrl().isBlank()) {
            return requestDto.getMediaUrl();
        }

        if (comp.getExample() != null &&
                comp.getExample().getHeaderHandle() != null &&
                !comp.getExample().getHeaderHandle().isEmpty()) {
            return comp.getExample().getHeaderHandle().get(0);
        }

        return null;
    }

    private String extractTemplateMediaUrl(TemplateCarouselCardComponent comp) {
        if (comp.getExample() != null &&
                comp.getExample().getHeaderHandle() != null &&
                !comp.getExample().getHeaderHandle().isEmpty()) {
            return comp.getExample().getHeaderHandle().get(0);
        }
        return null;
    }

    private Parameter buildMediaParameterWithFallback(MediaType mediaType, String mediaId, String mediaUrl) {
        if (mediaId == null && mediaUrl == null) {
            return null;
        }

        Parameter param = new Parameter();
        param.setType(mediaType.getValue().toLowerCase());

        Media mediaDto = createMediaDtoWithFallback(mediaType, mediaId, mediaUrl);

        switch (mediaType) {
            case DOCUMENT -> param.setDocument((Document) mediaDto);
            case IMAGE -> param.setImage((Image) mediaDto);
            case VIDEO -> param.setVideo((Video) mediaDto);
            default -> throw new InvalidMediaType("Unsupported media type: " + mediaType);
        }

        return param;
    }

    private Media createMediaDtoWithFallback(MediaType mediaType, String mediaId, String mediaUrl) {
        Media mediaDto = switch (mediaType) {
            case DOCUMENT -> new Document();
            case IMAGE -> new Image();
            case VIDEO -> new Video();
            default -> throw new InvalidMediaType("Unsupported media type: " + mediaType);
        };

        if (mediaId != null && !mediaId.isBlank()) {
            mediaDto.setId(mediaId);
            log.debug("Using media ID: {}", mediaId);
        } else if (mediaUrl != null && !mediaUrl.isBlank()) {
            mediaDto.setLink(mediaUrl);
            log.debug("Using media URL: {}", mediaUrl);
        }

        return mediaDto;
    }

    private <T extends TextParameter> T buildTextParameter(String text, Supplier<T> supplier) {
        T param = supplier.get();
        param.setType("text");
        param.setText(text);
        return param;
    }

    private Parameter buildProductParameter(String catalogueId, String productRetailerId) {
        Parameter parameter = new Parameter();
        parameter.setType("product");

        Product product = new Product();
        product.setCatalogId(catalogueId);
        product.setProductRetailerId(productRetailerId);
        parameter.setProduct(product);

        return parameter;
    }

    private Parameter buildLimitedTimeOfferParam(Long expirationTime) {
        Parameter p = new Parameter();
        p.setType(ComponentType.LIMITED_TIME_OFFER.getValue().toLowerCase());
        p.setLimitedTimeOffer(new LimitedTimeOffer(expirationTime));
        return p;
    }

    private <T> void addIfNotNull(List<T> list, T element) {
        if (element != null)
            list.add(element);
    }

    private <T> void addAllIfNotEmpty(List<T> list, List<T> elements) {
        if (elements != null && !elements.isEmpty())
            list.addAll(elements);
    }
}