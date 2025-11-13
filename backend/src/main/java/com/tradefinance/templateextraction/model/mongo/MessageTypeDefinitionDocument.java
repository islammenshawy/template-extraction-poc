package com.tradefinance.templateextraction.model.mongo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Database-driven SWIFT message type definitions
 * Replaces hardcoded MT message types
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "message_type_definitions")
public class MessageTypeDefinitionDocument {

    @Id
    private String id;

    private String messageType; // e.g., "MT700"
    private String category; // e.g., "Documentary Credits", "Payments"
    private String seriesCode; // e.g., "MT7XX"
    private String description;
    private String longDescription;

    // Field definitions for this message type
    private List<FieldDefinition> mandatoryFields;
    private List<FieldDefinition> optionalFields;

    // Processing rules
    private ProcessingRules processingRules;

    // Validation rules
    private List<ValidationRule> validationRules;

    // Template extraction settings
    private TemplateSettings templateSettings;

    private boolean active;
    private Integer priority;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldDefinition {
        private String fieldTag; // e.g., "20", "32B"
        private String fieldName;
        private String dataType; // ALPHANUMERIC, NUMERIC, DATE, AMOUNT, etc.
        private boolean required;
        private String pattern; // Regex pattern
        private String description;
        private List<String> allowedValues;
        private Integer minLength;
        private Integer maxLength;
        private String format; // e.g., "YYMMDD" for dates
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessingRules {
        private boolean enableClustering;
        private boolean enableTemplateMatching;
        private boolean requireManualReview;
        private Double minimumConfidence;
        private Integer maxClusterSize;
        private String processingPriority; // HIGH, MEDIUM, LOW
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationRule {
        private String ruleType; // FIELD_REQUIRED, CROSS_FIELD, BUSINESS_RULE
        private String fieldTag;
        private String expression; // e.g., "field32B.currency == field33B.currency"
        private String errorMessage;
        private String severity; // ERROR, WARNING, INFO
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TemplateSettings {
        private Double similarityThreshold;
        private Integer minMessagesForTemplate;
        private boolean autoGenerateTemplate;
        private List<String> variableFieldTags; // Fields that are expected to vary
        private List<String> fixedFieldTags; // Fields that should be constant
    }
}
