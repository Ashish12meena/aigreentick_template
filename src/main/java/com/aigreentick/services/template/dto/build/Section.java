package com.aigreentick.services.template.dto.build;




import java.util.List;

import lombok.Data;

@Data
public class Section {
    private String title;
    private List<SectionRows> rows;
    private List<ProductItem> productItems;
}
