package com.aigreentick.services.template.util.helper;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class JsonHelper {

    private JsonHelper() {
        // prevent instantiation
    }

    private static final ObjectMapper DEFAULT_MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private static final ObjectMapper SNAKE_CASE_MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public static <T> String serialize(T request) {
        try {
            return DEFAULT_MAPPER.writeValueAsString(request);
        } catch (Exception e) {
            log.error("Failed to serialize object of type {}", request != null ? request.getClass().getName() : "null", e);
            throw new IllegalStateException("JSON serialization failed", e);
        }
    }

    public static <T> String serializeWithSnakeCase(T request) {
        try {
            return SNAKE_CASE_MAPPER.writeValueAsString(request);
        } catch (Exception e) {
            log.error("Failed to serialize object with snake_case", e);
            throw new IllegalStateException("JSON serialization failed", e);
        }
    }
}
