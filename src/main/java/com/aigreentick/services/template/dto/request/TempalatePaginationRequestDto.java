package com.aigreentick.services.template.dto.request;

import jakarta.validation.constraints.Min;
import lombok.Data;


@Data
public class TempalatePaginationRequestDto {
    @Min(0)
    private int page = 0;

    @Min(1)
    private int size = 10;
}
