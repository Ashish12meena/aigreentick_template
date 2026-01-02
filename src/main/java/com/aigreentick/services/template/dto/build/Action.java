package com.aigreentick.services.template.dto.build;




import java.util.List;



import lombok.Data;

@Data
public class Action {
    private String thumbnailProductRetailerId;
    private List<Section> sections;
}
