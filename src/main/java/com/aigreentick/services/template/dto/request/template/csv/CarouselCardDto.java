package com.aigreentick.services.template.dto.request.template.csv;

import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * Represents a carousel card's parameters from CSV request.
 * 
 * Key difference from standard flow:
 * - These variables are the SAME for all contacts (no per-contact personalization)
 * - Variables map uses 1-based string keys: {"1": "value1", "2": "value2"}
 */
@Data
public class CarouselCardDto {

    /**
     * Optional ID for tracking
     */
    private Long id;
    
    /**
     * Body text (optional - template already has this)
     */
    private String body;
    
    /**
     * Image URL for this card's header
     * Overrides template default if provided
     */
    private String imageUrl;
    
    /**
     * Card index (0-based) - matches template card order
     */
    private Integer cardIndex;

    /**
     * Body variables for this card
     * Key: Variable index as string (1-based, e.g., "1", "2")
     * Value: The value to substitute
     * 
     * Example: {"1": "Product A", "2": "$99.99"}
     */
    private Map<String, String> variables;

    /**
     * Button configurations for this card
     * Only URL buttons need variables
     */
    private List<CarouselButtonDto> buttons;
}