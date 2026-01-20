package com.aigreentick.services.template.dto.request.template.normal;

import java.util.List;

import com.aigreentick.services.template.dto.request.template.csv.CarouselCardDto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request DTO for Normal WhatsApp template broadcast.
 * 
 * Key differences from CSV flow:
 * - variables: Comma-separated string (same for ALL contacts)
 * - No per-contact variable override for non-carousel
 * - No button variables for non-carousel templates
 * - Carousel uses same CarouselCardDto structure as CSV
 * 
 * Example request:
 * {
 *   "templateId": "123",
 *   "campName": "January Sale",
 *   "countryId": 91,
 *   "mobileNumbers": ["919876543210", "919876543211"],
 *   "variables": "John,Premium,50%",
 *   "carouselCards": [...]
 * }
 */
@Data
public class SendTemplateNormalRequestDto {

    /**
     * Template ID to use for broadcasting.
     * Required field.
     */
    @NotNull(message = "Template ID is required")
    @NotBlank(message = "Template ID cannot be blank")
    private String templateId;

    /**
     * Column name reference (optional, for tracking/mapping).
     */
    private String colName;

    /**
     * Country ID for phone number formatting.
     * Used when creating new contacts.
     */
    private Integer countryId;

    /**
     * Campaign name for tracking purposes.
     * Required field.
     */
    @NotNull(message = "Campaign name is required")
    @NotBlank(message = "Campaign name cannot be blank")
    private String campName;

    /**
     * Whether template contains media in HEADER.
     */
    private Boolean isMedia;

    /**
     * Type of media (IMAGE, VIDEO, DOCUMENT).
     * Required if isMedia is true.
     */
    private String mediaType;

    /**
     * Media URL if using URL-based media.
     * Used for HEADER media component.
     */
    private String mediaUrl;

    /**
     * List of mobile numbers to broadcast to.
     * Required field. Numbers should include country code.
     * 
     * Example: ["919876543210", "919876543211"]
     */
    @NotNull(message = "Mobile numbers are required")
    @NotEmpty(message = "Mobile numbers list cannot be empty")
    private List<String> mobileNumbers;

    /**
     * Schedule date for delayed broadcast (ISO format).
     * Optional. If null, broadcast starts immediately.
     * 
     * Example: "2025-01-25T10:00:00"
     */
    private String scheduleDate;

    /**
     * Non-carousel variables - SAME for ALL contacts.
     * Comma-separated string where each value maps to {{1}}, {{2}}, {{3}}...
     * 
     * NOTE: No button variable support for non-carousel templates.
     * Variables are used for HEADER (text) and BODY components only.
     * 
     * Example: "John,Premium Member,50% OFF"
     * This maps to:
     *   {{1}} -> "John"
     *   {{2}} -> "Premium Member"
     *   {{3}} -> "50% OFF"
     * 
     * Value Resolution Priority:
     * 1. This comma-separated value (if provided)
     * 2. Contact attribute (if template defaultValue maps to attribute)
     * 3. Template default value
     * 4. Example value from Facebook
     */
    private String variables;

    /**
     * Carousel card variables - same values for ALL contacts.
     * Each card has its own body variables and button variables.
     * 
     * Uses same structure as CSV flow (CarouselCardDto).
     * 
     * Example:
     * [
     *   {
     *     "cardIndex": 0,
     *     "imageUrl": "https://example.com/product1.jpg",
     *     "variables": {"1": "Product A", "2": "$99.99"},
     *     "buttons": [
     *       {"type": "URL", "variables": {"1": "sku-product-a"}}
     *     ]
     *   },
     *   {
     *     "cardIndex": 1,
     *     "imageUrl": "https://example.com/product2.jpg",
     *     "variables": {"1": "Product B", "2": "$149.99"},
     *     "buttons": [
     *       {"type": "URL", "variables": {"1": "sku-product-b"}}
     *     ]
     *   }
     * ]
     */
    private List<CarouselCardDto> carouselCards;
}