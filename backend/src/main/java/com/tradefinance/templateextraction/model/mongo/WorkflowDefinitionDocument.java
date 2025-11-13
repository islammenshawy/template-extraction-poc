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
 * Database-driven workflow definitions
 * Replaces hardcoded workflow states and transitions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "workflow_definitions")
public class WorkflowDefinitionDocument {

    @Id
    private String id;

    private String workflowName;
    private String workflowType; // MESSAGE_PROCESSING, TRANSACTION_APPROVAL, etc.
    private String description;
    private String version;

    private List<WorkflowStep> steps;
    private List<WorkflowTransition> transitions;
    private Map<String, String> initialState;

    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowStep {
        private String stepId;
        private String stepName;
        private String stepType; // MANUAL, AUTOMATIC, APPROVAL
        private String description;

        private List<String> requiredPermissions;
        private List<String> requiredFields;

        // Actions to perform at this step
        private List<Action> actions;

        // SLA
        private Integer maxDurationMinutes;
        private boolean notifyOnDelay;

        private Map<String, Object> configuration;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Action {
        private String actionType; // VALIDATE, NOTIFY, UPDATE_FIELD, CALL_API
        private String actionName;
        private Map<String, Object> parameters;
        private String onSuccess; // Next step ID
        private String onFailure; // Alternative step ID
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowTransition {
        private String fromStep;
        private String toStep;
        private String transitionName;
        private String condition; // e.g., "matchConfidence > 0.9"
        private List<String> requiredPermissions;
        private boolean notifyOnTransition;
        private List<String> notifyRoles;
    }
}
