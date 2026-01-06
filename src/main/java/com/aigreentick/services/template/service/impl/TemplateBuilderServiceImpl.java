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
import com.aigreentick.services.template.dto.response.PhoneBookResponseDto;
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
    private final ChatContactServiceImpl phoneBookEntryService;
    private final MediaServiceImpl mediaService;

    private static final int MAX_CARDS = MessageConstants.MAX_CARDS;
    private static final int MAX_BUTTONS_PER_CARD = MessageConstants.MAX_BUTTONS_PER_CARD;

    public List<MessageRequest> buildSendableTemplates(
            Long userId,
            List<String> phoneNumbers,
            TemplateDto template,
            SendTemplateRequestDto requestDto) {
        
        String defaultValue = Optional.ofNullable(requestDto.getDefaultValue()).orElse("default");
        TemplateCategory templateCategory = TemplateCategory.valueOf(template.getCategory());

        return switch (templateCategory) {
            case AUTHENTICATION -> buildAuthenticationSendTemplates(phoneNumbers, template, requestDto);
            case MARKETING, UTILITY -> {
                Map<String, Map<String, String>> parameters = buildParameters(
                        requestDto, phoneNumbers, template, userId, defaultValue).getData();
                yield buildMarketingSendTemplates(phoneNumbers, template, parameters, requestDto);
            }
            default -> throw new InvalidTemplateCategory("Unsupported template category: " + templateCategory);
        };
    }

    // ====================== AUTHENTICATION ======================

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

    // ====================== MARKETING/UTILITY ======================

    private List<MessageRequest> buildMarketingSendTemplates(
            List<String> phoneNumbers,
            TemplateDto template,
            Map<String, Map<String, String>> variables,
            SendTemplateRequestDto requestDto) {

        return phoneNumbers.stream()
                .map(phoneNumber -> buildMarketingSendTemplate(
                        phoneNumber, template, variables.get(phoneNumber), requestDto))
                .toList();
    }

    public MessageRequest buildMarketingSendTemplate(
            String phoneNumber,
            TemplateDto template,
            Map<String, String> variables,
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
                    Component header = buildHeaderComponent(comp, template, variables, requestDto);
                    addIfNotNull(components, header);
                }
                case BODY -> {
                    Component body = buildBodyComponent(template, variables);
                    addIfNotNull(components, body);
                }
                case BUTTONS -> {
                    List<Component> buttonComponents = buildButtonComponents(template, comp, variables);
                    addAllIfNotEmpty(components, buttonComponents);
                }
                case LIMITED_TIME_OFFER -> {
                    Component lto = buildLimitedTimeOfferComponent(requestDto);
                    addIfNotNull(components, lto);
                }
                case CAROUSEL -> {
                    Component carousel = buildCarouselComponent(comp, template, variables, requestDto);
                    addIfNotNull(components, carousel);
                }
                default -> throw new InvalidTemplateComponentType("Unsupported component type: " + comp.getType());
            }
        }

        addCopyCodeButtonIfPresent(requestDto, template, components);

        sendableTemplate.setComponents(components);
        messageRequest.setTemplate(sendableTemplate);

        return messageRequest;
    }

    // ====================== HEADER COMPONENT ======================

    private Component buildHeaderComponent(
            TemplateComponentDto comp,
            TemplateDto template,
            Map<String, String> variables,
            SendTemplateRequestDto requestDto) {
        
        if ("TEXT".equalsIgnoreCase(comp.getFormat())) {
            return buildHeaderTextComponent(template, variables);
        } else {
            return buildHeaderMediaComponent(comp, requestDto);
        }
    }

    private Component buildHeaderTextComponent(TemplateDto template, Map<String, String> parameters) {
        List<TemplateTextDto> headerTexts = template.getTexts().stream()
                .filter(t -> ComponentType.HEADER.getValue().equalsIgnoreCase(t.getType()))
                .toList();

        if (headerTexts.isEmpty()) return null;

        List<Parameter> componentParameters = new ArrayList<>();
        for (TemplateTextDto templateText : headerTexts) {
            String runtimeValue = parameters.get(templateText.getText());
            if (runtimeValue != null) {
                componentParameters.add(buildTextParameter(runtimeValue, Parameter::new));
            }
        }
        if (componentParameters.isEmpty()) return null;

        Component component = new Component();
        component.setType(ComponentType.HEADER.getValue().toLowerCase());
        component.setParameters(componentParameters);
        return component;
    }

    private Component buildHeaderMediaComponent(
            TemplateComponentDto comp,
            SendTemplateRequestDto requestDto) {
        
        MediaType mediaType = MediaType.fromValue(comp.getFormat());
        
        // Determine media source: frontend mediaId or template's stored URL
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

    /**
     * Resolves media ID from request (frontend provided)
     */
    private String resolveMediaId(SendTemplateRequestDto requestDto) {
        if (Boolean.TRUE.equals(requestDto.getIsMedia()) && requestDto.getMediaId() > 0) {
            return mediaService.getMediaIdById(requestDto.getMediaId());
        }
        return null;
    }

    /**
     * Resolves media URL from request or template's stored example
     */
    private String resolveMediaUrl(TemplateComponentDto comp, SendTemplateRequestDto requestDto) {
        // First check if URL provided directly in request
        if (requestDto.getMediaUrl() != null && !requestDto.getMediaUrl().isBlank()) {
            return requestDto.getMediaUrl();
        }
        
        // Fallback to template's stored URL from example.headerHandle
        if (comp.getExample() != null && 
            comp.getExample().getHeaderHandle() != null && 
            !comp.getExample().getHeaderHandle().isEmpty()) {
            return comp.getExample().getHeaderHandle().get(0);
        }
        
        return null;
    }

    // ====================== CAROUSEL COMPONENT ======================

    private Component buildCarouselComponent(
            TemplateComponentDto comp,
            TemplateDto template,
            Map<String, String> variables,
            SendTemplateRequestDto requestDto) {
        
        if (comp.getCards() == null || comp.getCards().isEmpty()) {
            throw new CarouselConfigurationException("Carousel component must contain at least one card");
        }
        if (comp.getCards().size() > MAX_CARDS) {
            throw new CarouselConfigurationException("Carousel supports max " + MAX_CARDS + " cards");
        }

        // Queue of media IDs from frontend (can be empty)
        Queue<String> mediaIdQueue = new ConcurrentLinkedQueue<>(
                Optional.ofNullable(requestDto.getMediaIdsForCarosel()).orElse(Collections.emptyList()));
        
        Queue<String> productRetailerIds = new ConcurrentLinkedQueue<>(
                Optional.ofNullable(requestDto.getProductRetailerIds()).orElse(Collections.emptyList()));

        Component component = new Component();
        component.setType(comp.getType().toLowerCase());

        List<Card> cards = comp.getCards().stream()
                .map(templateCard -> buildCard(
                        templateCard, template, variables, mediaIdQueue, productRetailerIds, requestDto))
                .toList();

        component.setCards(cards);
        return component;
    }

    private Card buildCard(
            TemplateComponentCardsDto templateCard,
            TemplateDto template,
            Map<String, String> variables,
            Queue<String> mediaIdQueue,
            Queue<String> productQueue,
            SendTemplateRequestDto requestDto) {

        Card card = new Card();
        card.setCardIndex(templateCard.getIndex());

        List<CarouselComponent> carouselComponents = new ArrayList<>();

        for (TemplateCarouselCardComponent comp : templateCard.getComponents()) {
            List<CarouselComponent> builtComponents = buildCarouselCardComponent(
                    comp, template, variables, mediaIdQueue, productQueue, requestDto);
            addAllIfNotEmpty(carouselComponents, builtComponents);
        }

        card.setComponents(carouselComponents);
        return card;
    }

    private List<CarouselComponent> buildCarouselCardComponent(
            TemplateCarouselCardComponent comp,
            TemplateDto template,
            Map<String, String> variables,
            Queue<String> mediaIdQueue,
            Queue<String> productQueue,
            SendTemplateRequestDto requestDto) {

        return switch (ComponentType.fromValue(comp.getType())) {
            case HEADER -> {
                MediaType mediaType = MediaType.fromValue(comp.getFormat());
                if (mediaType == MediaType.PRODUCT) {
                    yield List.of(buildCarouselHeaderProductComponent(comp, requestDto, productQueue.poll()));
                } else {
                    yield List.of(buildCarouselHeaderMediaComponent(comp, mediaIdQueue.poll()));
                }
            }
            case BODY -> {
                CarouselComponent body = buildCarouselBodyComponent(template, variables);
                yield body != null ? List.of(body) : Collections.emptyList();
            }
            case BUTTONS -> buildCarouselButtonComponents(template, comp, variables);
            default -> throw new InvalidTemplateComponentType("Unsupported carousel component type: " + comp.getType());
        };
    }

    /**
     * Builds carousel header with media - uses ID if provided, otherwise falls back to template URL
     */
    private CarouselComponent buildCarouselHeaderMediaComponent(
            TemplateCarouselCardComponent comp,
            String mediaIdFromQueue) {
        
        MediaType mediaType = MediaType.fromValue(comp.getFormat());
        
        // Try to get URL from template's stored example
        String templateUrl = extractTemplateMediaUrl(comp);
        
        // Build parameter with fallback logic
        Parameter mediaParam = buildMediaParameterWithFallback(mediaType, mediaIdFromQueue, templateUrl);
        
        if (mediaParam == null) {
            throw new CarouselConfigurationException(
                    "No media ID or URL available for carousel card header. " +
                    "Either provide mediaIdsForCarosel or ensure template has stored media URLs.");
        }

        CarouselComponent headerComponent = new CarouselComponent();
        headerComponent.setType(ComponentType.HEADER.getValue().toLowerCase());
        headerComponent.setParameters(List.of(mediaParam));
        
        return headerComponent;
    }

    /**
     * Extracts media URL from carousel card component's example
     */
    private String extractTemplateMediaUrl(TemplateCarouselCardComponent comp) {
        if (comp.getExample() != null && 
            comp.getExample().getHeaderHandle() != null && 
            !comp.getExample().getHeaderHandle().isEmpty()) {
            return comp.getExample().getHeaderHandle().get(0);
        }
        return null;
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

    private CarouselComponent buildCarouselBodyComponent(TemplateDto template, Map<String, String> variables) {
        List<TemplateTextDto> bodyTexts = template.getTexts().stream()
                .filter(t -> ComponentType.BODY.getValue().equalsIgnoreCase(t.getType()))
                .toList();

        List<Parameter> params = bodyTexts.stream()
                .map(t -> variables.get(t.getText()))
                .filter(Objects::nonNull)
                .map(val -> buildTextParameter(val, Parameter::new))
                .toList();

        if (params.isEmpty()) return null;

        CarouselComponent component = new CarouselComponent();
        component.setType(ComponentType.BODY.getValue().toLowerCase());
        component.setParameters(params);
        return component;
    }

    private List<CarouselComponent> buildCarouselButtonComponents(
            TemplateDto template,
            TemplateCarouselCardComponent comp,
            Map<String, String> variables) {
        
        List<TemplateCarouselButton> buttons = Optional.ofNullable(comp.getButtons())
                .orElse(Collections.emptyList());

        if (buttons.size() > MAX_BUTTONS_PER_CARD) {
            throw new CarouselConfigurationException(
                    "Carousel card supports at most " + MAX_BUTTONS_PER_CARD + " buttons");
        }

        String payload = "payload";
        List<CarouselComponent> buttonComponents = new ArrayList<>();

        for (TemplateCarouselButton button : buttons) {
            CarouselComponent btnComp = switch (ButtonTypes.fromValue(button.getType())) {
                case URL -> buildCarouselUrlButton(template, button, variables);
                case QUICK_REPLY -> buildCarouselQuickReplyButton(button, payload);
                default -> null;
            };
            addIfNotNull(buttonComponents, btnComp);
        }

        return buttonComponents;
    }

    private CarouselComponent buildCarouselUrlButton(
            TemplateDto template,
            TemplateCarouselButton button,
            Map<String, String> variables) {
        
        Parameter param = template.getTexts().stream()
                .filter(t -> "BUTTON".equalsIgnoreCase(t.getType()))
                .map(t -> buildTextParam(variables, t.getText()))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        if (param == null) {
            log.debug("Skipping URL carousel button at index {} - no runtime parameter", button.getIndex());
            return null;
        }

        CarouselComponent component = new CarouselComponent();
        component.setType("button");
        component.setSubType(button.getType().toLowerCase());
        component.setIndex(button.getIndex());
        component.setParameters(List.of(param));
        return component;
    }

    private CarouselComponent buildCarouselQuickReplyButton(TemplateCarouselButton button, String payload) {
        Parameter param = new Parameter();
        param.setType("payload");
        param.setPayload(payload);

        CarouselComponent component = new CarouselComponent();
        component.setType("button");
        component.setSubType(button.getType().toLowerCase());
        component.setIndex(button.getIndex());
        component.setParameters(List.of(param));
        return component;
    }

    // ====================== BODY COMPONENT ======================

    private Component buildBodyComponent(TemplateDto template, Map<String, String> variables) {
        List<TemplateTextDto> bodyTexts = template.getTexts().stream()
                .filter(t -> ComponentType.BODY.getValue().equalsIgnoreCase(t.getType()))
                .toList();

        List<Parameter> params = bodyTexts.stream()
                .map(t -> variables.get(t.getText()))
                .filter(Objects::nonNull)
                .map(val -> buildTextParameter(val, Parameter::new))
                .toList();

        if (params.isEmpty()) return null;

        Component component = new Component();
        component.setType(ComponentType.BODY.getValue().toLowerCase());
        component.setParameters(params);
        return component;
    }

    // ====================== BUTTON COMPONENTS ======================

    private List<Component> buildButtonComponents(
            TemplateDto template,
            TemplateComponentDto templateComponent,
            Map<String, String> variables) {

        List<Component> buttonComponents = new ArrayList<>();

        for (TemplateComponentButtonDto button : templateComponent.getButtons()) {
            if (ButtonTypes.fromValue(button.getType()) == ButtonTypes.URL) {
                Parameter param = template.getTexts().stream()
                        .filter(t -> "BUTTON".equalsIgnoreCase(t.getType()))
                        .map(t -> buildTextParam(variables, t.getText()))
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);
                
                if (param != null) {
                    Component component = new Component();
                    component.setType("button");
                    component.setSubType(button.getType().toLowerCase());
                    component.setIndex(String.valueOf(button.getIndex()));
                    component.setParameters(List.of(param));
                    buttonComponents.add(component);
                }
            }
        }

        return buttonComponents.isEmpty() ? null : buttonComponents;
    }

    private void addCopyCodeButtonIfPresent(
            SendTemplateRequestDto requestDto,
            TemplateDto template,
            List<Component> components) {

        if (requestDto.getCopyCode() == null) return;

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

    // ====================== LIMITED TIME OFFER ======================

    private Component buildLimitedTimeOfferComponent(SendTemplateRequestDto requestDto) {
        if (requestDto.getExpirationTimeMs() == null) {
            throw new IllegalArgumentException("expiration_time_ms required for LTO templates");
        }

        Component component = new Component();
        component.setType(ComponentType.LIMITED_TIME_OFFER.getValue().toLowerCase());
        component.setParameters(List.of(buildLimitedTimeOfferParam(requestDto.getExpirationTimeMs())));
        return component;
    }

    // ====================== PARAMETER BUILDERS ======================

    /**
     * Builds media parameter with fallback logic:
     * - If mediaId is provided, uses 'id' field
     * - If only URL is available, uses 'link' field
     */
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

    /**
     * Creates Media DTO with appropriate field set:
     * - 'id' field when mediaId is available (uploaded media)
     * - 'link' field when only URL is available (external URL)
     */
    private Media createMediaDtoWithFallback(MediaType mediaType, String mediaId, String mediaUrl) {
        Media mediaDto = switch (mediaType) {
            case DOCUMENT -> new Document();
            case IMAGE -> new Image();
            case VIDEO -> new Video();
            default -> throw new InvalidMediaType("Unsupported media type: " + mediaType);
        };

        // Priority: mediaId (uploaded) > mediaUrl (link)
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

    private Parameter buildTextParam(Map<String, String> parameters, String key) {
        if (parameters.containsKey(key)) {
            Parameter param = new Parameter();
            param.setType("text");
            param.setText(parameters.get(key));
            return param;
        }
        return null;
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

    // ====================== PHONE BOOK PARAMETERS ======================

    private PhoneBookResponseDto buildParameters(
            SendTemplateRequestDto requestDto,
            List<String> filteredMobileNumbers,
            TemplateDto template,
            Long userId,
            String defaultValue) {
        
        if (requestDto.isFullyPrameterized()) {
            Map<String, String> sharedParams = Optional.ofNullable(requestDto.getParameters())
                    .orElseGet(Map::of);

            Map<String, Map<String, String>> data = filteredMobileNumbers.stream()
                    .collect(Collectors.toMap(
                            number -> number,
                            number -> new HashMap<>(sharedParams)));

            PhoneBookResponseDto dto = new PhoneBookResponseDto();
            dto.setData(data);
            return dto;
        } else {
            List<String> keys = template.getTexts().stream()
                    .map(TemplateTextDto::getText)
                    .toList();

            PhoneBookResponseDto parameters = phoneBookEntryService
                    .getParamsForPhoneNumbers(filteredMobileNumbers, keys, userId, defaultValue);

            if (requestDto.getParameters() != null) {
                parameters.getData().values().forEach(map -> map.putAll(requestDto.getParameters()));
            }

            return parameters;
        }
    }

    // ====================== UTILITY METHODS ======================

    private <T> void addIfNotNull(List<T> list, T element) {
        if (element != null) list.add(element);
    }

    private <T> void addAllIfNotEmpty(List<T> list, List<T> elements) {
        if (elements != null && !elements.isEmpty()) list.addAll(elements);
    }
}