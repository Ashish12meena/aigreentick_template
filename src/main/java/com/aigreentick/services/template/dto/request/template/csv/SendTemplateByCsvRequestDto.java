package com.aigreentick.services.template.dto.request.template.csv;

import java.util.List;

import lombok.Data;

@Data
public class SendTemplateByCsvRequestDto {
    
    /**
     * Template ID to use for broadcasting
     */
    private String templateId;
    
    /**
     * Column name reference (for CSV mapping)
     */
    private String colName;
    
    /**
     * Country ID for phone number formatting
     */
    private Integer countryId;
    
    /**
     * Campaign name for tracking
     */
    private String campName;

    /**
     * Whether template contains media
     */
    private Boolean isMedia;
    
    /**
     * Type of media (IMAGE, VIDEO, DOCUMENT)
     */
    private String mediaType;
    
    /**
     * Media URL if using URL-based media
     */
    private String mediaUrl;

    /**
     * List of mobile numbers to broadcast to
     */
    private List<Long> mobileNumbers;
    
    /**
     * Schedule date for delayed broadcast (ISO format)
     */
    private String scheduleDate;

    /**
     * Non-carousel variables - can be per-contact or global
     * If VariableGroupDto.mobile is present: per-contact override
     * If VariableGroupDto.mobile is null: global default for all contacts
     * 
     * Example:
     * [
     *   { "mobile": null, "variable": [{"variable": 1, "value": "Default Name"}] },
     *   { "mobile": 919876543210, "variable": [{"variable": 1, "value": "John"}] }
     * ]
     */
    private List<VariableGroupDto> variables;
    
    /**
     * Carousel card variables - same values for ALL contacts
     * Each card has its own body variables and button variables
     * 
     * Example:
     * [
     *   {
     *     "cardIndex": 0,
     *     "imageUrl": "https://...",
     *     "variables": {"1": "Card 1 Value 1", "2": "Card 1 Value 2"},
     *     "buttons": [
     *       {"type": "URL", "variables": {"1": "button-param"}}
     *     ]
     *   }
     * ]
     */
    private List<CarouselCardDto> carouselCards;


    //use by schedular
    private Boolean isSchedulerExecution = false;
}