package com.aigreentick.services.template.dto.request;

import java.util.List;

import com.aigreentick.services.template.enums.MediaFormat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TemplateCarouselCardComponentRequest {
     private String type;
    private MediaFormat format; 
    private TemplateCarouselExampleRequest example;
    private String text;
    private List<TemplateCarouselButtonRequest> buttons;
}
