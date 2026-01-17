package com.aigreentick.services.template.dto.request.template.csv;

import java.util.List;

import lombok.Data;

@Data
public class SendTemplateByCsvRequestDto {
        private String templateId;
    private String colName;
    private Integer countryId;
    private String campName;

    private Boolean isMedia;
    private String mediaType;

    private List<Long> mobileNumbers;
    private String scheduleDate;

    private List<VariableGroupDto> variables;
    private List<CarouselCardDto> carouselCards;
}
