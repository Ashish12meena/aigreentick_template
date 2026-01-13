package com.aigreentick.services.template.dto.request.template;

import java.util.List;

import com.aigreentick.services.template.enums.ButtonTypes;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL) 
public class TemplateCarouselButtonRequest {
    private ButtonTypes type; // quick_reply, url, phone_number
    private String text;
    private Integer index;

    // optional fields depending on type
    private String url;
    private List<String> example; // for URL button variable example
    private String phoneNumber;
}
