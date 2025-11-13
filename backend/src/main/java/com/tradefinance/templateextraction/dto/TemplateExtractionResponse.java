package com.tradefinance.templateextraction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateExtractionResponse {

    private int totalMessages;
    private int clustersCreated;
    private List<TemplateInfo> templates;
    private String status;
    private String message;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TemplateInfo {
        private String templateId;
        private String messageType;
        private int messageCount;
        private double confidence;
        private String description;
    }
}
