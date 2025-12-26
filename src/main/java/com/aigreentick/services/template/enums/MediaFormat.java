package com.aigreentick.services.template.enums;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enum representing supported media formats in WhatsApp template components.
 * These formats define the type of content (text or media) attached to a component.
 */
public enum MediaFormat {

    TEXT("TEXT"),
    IMAGE("IMAGE"),
    VIDEO("VIDEO"),
    DOCUMENT("DOCUMENT");

    /**
     * Constants class for reusing raw string values without calling enum methods.
     */
    public static class Constants {
        public static final String TEXT = "TEXT";
        public static final String IMAGE = "IMAGE";
        public static final String VIDEO = "VIDEO";
        public static final String DOCUMENT = "DOCUMENT";
    }

    private final String value;

    MediaFormat(String value) {
        this.value = value;
    }

    /**
     * Used when serializing enum to JSON.
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Creates a MediaFormat enum from a string value (case-insensitive).
     *
     * @param value the string representation of the media format
     * @return the matching MediaFormat
     * @throws IllegalArgumentException if the format is not recognized
     */
    @JsonCreator
    public static MediaFormat fromValue(String value) {
        for (MediaFormat format : MediaFormat.values()) {
            if (format.value.equalsIgnoreCase(value)) {
                return format;
            }
        }
        throw new IllegalArgumentException("Unknown media format: " + value);
    }
}

