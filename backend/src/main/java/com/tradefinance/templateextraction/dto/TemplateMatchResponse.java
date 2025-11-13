package com.tradefinance.templateextraction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateMatchResponse {

    private String templateId;
    private String messageType;
    private double matchConfidence;
    private Map<String, Object> extractedFields;
    private Map<String, Object> suggestedValues;
    private String llmComparison;
    private boolean requiresManualReview;
    private String message;
}
