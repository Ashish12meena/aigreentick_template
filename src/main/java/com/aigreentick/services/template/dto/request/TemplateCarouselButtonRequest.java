package com.aigreentick.services.template.dto.request;

import java.util.List;

import com.aigreentick.services.template.enums.ButtonTypes;

import lombok.Data;

@Data
public class TemplateCarouselButtonRequest {
    private ButtonTypes type; // quick_reply, url, phone_number
    private String text;
    private Integer index;

    // optional fields depending on type
    private String url;
    private List<String> example; // for URL button variable example
    private String phoneNumber;
}
