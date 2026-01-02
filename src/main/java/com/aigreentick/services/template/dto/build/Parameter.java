package com.aigreentick.services.template.dto.build;

import java.util.List;


import lombok.Data;

@Data
public class Parameter implements TextParameter, MediaParameter  {
    private String type;
    private String text;
    private List<TapTargetConfiguration> tapTargetConfiguration;
    private Action action;
    private String couponCode;
    private Image image;
    private Document document;
    private Video video;
    private LimitedTimeOffer limitedTimeOffer;
    private String payload;
    private Product product;
}

