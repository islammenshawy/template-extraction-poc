package com.tradefinance.templateextraction.model.mongo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Database-driven field type detection rules
 * Replaces hardcoded regex patterns
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "field_type_rules")
public class FieldTypeRuleDocument {

    @Id
    private String id;

    private String fieldType; // AMOUNT, DATE, CODE, NUMERIC, etc.
    private String description;
    private Integer priority; // Higher priority rules are checked first

    // Detection patterns
    private List<DetectionPattern> patterns;

    // Transformation rules
    private List<TransformationRule> transformations;

    // Validation rules
    private List<ValidationRule> validations;

    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetectionPattern {
        private String patternType; // REGEX, PREFIX, SUFFIX, LENGTH, CONTAINS
        private String pattern;
        private String description;
        private Double confidence; // How confident we are this pattern indicates this field type
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransformationRule {
        private String transformationType; // EXTRACT, FORMAT, NORMALIZE, PARSE
        private String sourcePattern;
        private String targetFormat;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationRule {
        private String validationType; // RANGE, FORMAT, CHECKSUM, LOOKUP
        private String validationExpression;
        private String errorMessage;
        private String severity;
    }
}
