package com.aigreentick.services.template.dto.request;

import java.util.List;

import com.aigreentick.services.template.enums.ComponentType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;


@Data
@JsonInclude(JsonInclude.Include.NON_NULL) 
@JsonIgnoreProperties(ignoreUnknown = true)
public class TemplateComponentRequest {
    /**
     * Type of the component.
     * Examples: HEADER, BODY, FOOTER, BUTTONS, CAROUSEL
     */
    private ComponentType type;

    /**
     * Format of the component.
     * Examples: TEXT, IMAGE, VIDEO, DOCUMENT
     */
    private String format;
    

    private String text;

    /**
     * URL of an image for IMAGE type components.
     */
    private String imageUrl;

    /**
     * URL of media (video or document) for MEDIA type components.
     */
    private String mediaUrl;

    /**
     * Indicates whether a security recommendation should be added for this
     * component.
     */
    private Boolean addSecurityRecommendation;

    private Integer codeExpirationMinutes;

    /**
     * List of buttons associated with this component.
     * Each button must conform to WhatsApp template rules.
     */
    private List<TemplateComponentButtonRequest> buttons;

    /**
     * List of carousel cards associated with this component.
     * Only applicable if the component type is CAROUSEL.
     */
    private List<TemplateComponentCardsRequest> cards;

    private TemplateExampleRequest example;
}
