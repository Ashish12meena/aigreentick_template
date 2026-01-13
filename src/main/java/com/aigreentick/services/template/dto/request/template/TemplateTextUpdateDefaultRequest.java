package com.aigreentick.services.template.dto.request.template;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TemplateTextUpdateDefaultRequest {
    @NotEmpty(message = "Defaults list cannot be empty")
    @Valid
    private List<TemplateTextDefaultItems> defaults;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TemplateTextDefaultItems {

        /**
         * The ID of the TemplateText entity (optional - can use textIndex + type
         * instead)
         */
        private Long id;

        /**
         * The type of the variable: HEADER, BODY, BUTTON
         */
        private String type;

        /**
         * The index of the variable within its type (0-based)
         */
        private Integer textIndex;

        /**
         * For carousel templates - the card index (0-based)
         */
        private Integer cardIndex;

        /**
         * Whether this is a carousel variable
         */
        private Boolean isCarousel;

        /**
         * The default value to set for this variable
         */
        private String defaultValue;
    }

}
