package com.tradefinance.templateextraction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Structured LLM analysis with field-level findings
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StructuredAnalysis {

    /**
     * Brief transaction summary
     */
    private String transactionSummary;

    /**
     * Field-level findings grouped by field
     */
    private List<FieldFinding> fieldFindings;

    /**
     * Overall risk assessment
     */
    private RiskLevel overallRisk;

    /**
     * Final recommendation
     */
    private String recommendation;

    /**
     * Any additional notes
     */
    private String notes;

    public enum RiskLevel {
        LOW,      // Green - Routine transaction
        MEDIUM,   // Yellow - Review recommended
        HIGH      // Red - Requires approval
    }

    /**
     * Get count of findings by severity
     */
    public long getCriticalCount() {
        return fieldFindings == null ? 0 : fieldFindings.stream()
                .filter(f -> f.getSeverity() == FieldFinding.Severity.CRITICAL)
                .count();
    }

    public long getWarningCount() {
        return fieldFindings == null ? 0 : fieldFindings.stream()
                .filter(f -> f.getSeverity() == FieldFinding.Severity.WARNING)
                .count();
    }

    public long getInfoCount() {
        return fieldFindings == null ? 0 : fieldFindings.stream()
                .filter(f -> f.getSeverity() == FieldFinding.Severity.INFO)
                .count();
    }
}
