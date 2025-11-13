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
 * Database-driven user preferences
 * No hardcoded user settings
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "user_preferences")
public class UserPreferencesDocument {

    @Id
    private String id;

    private String userId;
    private String userName;
    private String email;

    // UI Preferences
    private UIPreferences uiPreferences;

    // Notification preferences
    private NotificationPreferences notificationPreferences;

    // Processing preferences
    private ProcessingPreferences processingPreferences;

    // Dashboard preferences
    private DashboardPreferences dashboardPreferences;

    // Custom preferences
    private Map<String, Object> customPreferences;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UIPreferences {
        private String theme; // light, dark, auto
        private String language;
        private String dateFormat;
        private String timeFormat;
        private String currencyFormat;
        private Integer itemsPerPage;
        private String defaultView; // table, card, list
        private List<String> favoriteMessageTypes;
        private Map<String, Boolean> featureToggles;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationPreferences {
        private boolean emailNotifications;
        private boolean pushNotifications;
        private boolean smsNotifications;

        private List<String> notifyOn; // MESSAGE_UPLOADED, TEMPLATE_CREATED, etc.
        private String notificationFrequency; // IMMEDIATE, HOURLY, DAILY
        private List<String> notificationChannels; // EMAIL, SMS, PUSH

        private Map<String, Boolean> eventNotifications;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessingPreferences {
        private boolean autoMatch;
        private boolean autoApprove;
        private Double autoApproveThreshold;

        private List<String> preferredTemplates;
        private List<String> ignoredTemplates;

        private String defaultStatus;
        private boolean requireReviewForLowConfidence;
        private Double lowConfidenceThreshold;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardPreferences {
        private List<DashboardWidget> widgets;
        private String layout; // GRID, LIST, CUSTOM
        private Integer refreshInterval; // in seconds

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class DashboardWidget {
            private String widgetId;
            private String widgetType;
            private String title;
            private Integer position;
            private Integer width;
            private Integer height;
            private Map<String, Object> configuration;
            private boolean visible;
        }
    }
}
