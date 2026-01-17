package com.aigreentick.services.template.dto.request.template.csv;

import java.util.Map;

import lombok.Data;

/**
 * Represents a carousel card button's parameters from CSV request.
 */
@Data
public class CarouselButtonDto {

    /**
     * Optional ID for tracking
     */
    private Long id;
    
    /**
     * Button type: QUICK_REPLY, URL
     */
    private String type;
    
    /**
     * Button text (optional - template already has this)
     */
    private String text;
    
    /**
     * URL template (optional - template already has this)
     */
    private String url;

    /**
     * Variables for URL buttons (dynamic URL suffix)
     * Key: Variable index as string (1-based, e.g., "1")
     * Value: The value to substitute in URL
     * 
     * Example: For URL "https://example.com/product/{{1}}"
     * variables: {"1": "SKU-12345"}
     * Result: "https://example.com/product/SKU-12345"
     */
    private Map<String, String> variables;
}