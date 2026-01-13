package com.aigreentick.services.template.dto.request.template;

import java.util.List;

import com.aigreentick.services.template.enums.MediaFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TemplateCarouselCardComponentRequest {
    private String type;
    private MediaFormat format;
    private TemplateCarouselExampleRequest example;
    private String text;
    private List<TemplateCarouselButtonRequest> buttons;
}
