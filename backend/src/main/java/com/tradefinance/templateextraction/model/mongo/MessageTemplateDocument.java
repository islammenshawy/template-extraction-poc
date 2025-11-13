package com.tradefinance.templateextraction.model.mongo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "message_templates")
public class MessageTemplateDocument {

    @Id
    private String id;

    private String messageType;
    private String templateContent;
    private List<VariableField> variableFields;
    private Integer clusterId;
    private Integer messageCount;
    private LocalDateTime createdAt;
    private LocalDateTime lastUpdated;
    private String createdBy;
    private String description;
    private Double confidence;

    // Sample messages used to create the template
    private List<String> sampleMessageIds;

    // Template statistics
    private TemplateStatistics statistics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariableField {
        private String fieldName;
        private String fieldTag;
        private FieldType type;
        private List<String> sampleValues;
        private boolean required;
        private String pattern;
        private String description;

        public enum FieldType {
            ALPHANUMERIC,
            NUMERIC,
            DATE,
            AMOUNT,
            CODE,
            TEXT,
            CURRENCY,
            COUNTRY_CODE,
            BANK_CODE
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TemplateStatistics {
        private int totalMatches;
        private int successfulMatches;
        private int failedMatches;
        private double averageConfidence;
        private LocalDateTime lastMatchedAt;
    }
}
