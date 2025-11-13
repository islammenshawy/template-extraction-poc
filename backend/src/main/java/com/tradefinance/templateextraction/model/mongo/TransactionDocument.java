package com.tradefinance.templateextraction.model.mongo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "transactions")
public class TransactionDocument {

    @Id
    private String id;

    private String swiftMessageId;
    private String templateId;
    private String messageType;

    // Extracted and processed data
    private Map<String, Object> extractedData;
    private Map<String, Object> userEnteredData;
    private Map<String, Object> validatedData;

    // Matching information
    private Double matchConfidence;
    private List<String> matchedTemplateIds; // Multiple templates might match
    private MatchingDetails matchingDetails;

    // Processing metadata
    private LocalDateTime processedAt;
    private String processedBy;
    private TransactionStatus status;
    private String remarks;

    // Business data
    private String buyerId;
    private String sellerId;
    private String documentaryCreditNumber;
    private BigDecimal amount;
    private String currency;
    private LocalDateTime shipmentDate;
    private LocalDateTime expiryDate;

    // Audit trail
    private List<AuditEntry> auditTrail;
    private Map<String, String> metadata;

    // Workflow
    private WorkflowState workflowState;

    public enum TransactionStatus {
        PENDING,
        MATCHED,
        VALIDATED,
        APPROVED,
        REJECTED,
        COMPLETED,
        FAILED,
        ON_HOLD,
        CANCELLED
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MatchingDetails {
        private String primaryTemplateId;
        private List<AlternativeMatch> alternativeMatches;
        private Map<String, Double> fieldConfidences;
        private List<String> warnings;
        private List<String> suggestions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlternativeMatch {
        private String templateId;
        private Double confidence;
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditEntry {
        private LocalDateTime timestamp;
        private String action;
        private String performedBy;
        private String details;
        private TransactionStatus previousStatus;
        private TransactionStatus newStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowState {
        private String currentStep;
        private List<String> completedSteps;
        private List<String> pendingSteps;
        private Map<String, String> stepData;
        private LocalDateTime lastTransitionAt;
    }
}
