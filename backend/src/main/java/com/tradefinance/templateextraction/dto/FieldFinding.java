package com.tradefinance.templateextraction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents a field-level finding from LLM analysis with severity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldFinding {

    /**
     * SWIFT field tag (e.g., "32B", "43P", "44C")
     */
    private String fieldTag;

    /**
     * Field name in business terms (e.g., "Currency & Amount", "Partial Shipments")
     */
    private String fieldName;

    /**
     * Severity level of the finding
     */
    private Severity severity;

    /**
     * Human-readable description of what was found
     */
    private String description;

    /**
     * Value in the actual transaction
     */
    private String actualValue;

    /**
     * Expected value from template (if applicable)
     */
    private String expectedValue;

    /**
     * Business impact explanation
     */
    private String businessImpact;

    /**
     * Recommended action
     */
    private String recommendation;

    public enum Severity {
        CRITICAL,   // Red - Requires immediate attention
        WARNING,    // Yellow - Should be reviewed
        INFO,       // Blue - Informational, expected variation
        ACCEPTABLE  // Green - Within acceptable range
    }
}
