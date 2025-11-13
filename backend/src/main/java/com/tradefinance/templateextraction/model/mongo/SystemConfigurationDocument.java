package com.tradefinance.templateextraction.model.mongo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * System-wide configuration stored in database
 * Replaces application.yml static configuration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "system_configuration")
public class SystemConfigurationDocument {

    @Id
    private String id;

    private String configKey;
    private String configGroup; // e.g., "clustering", "embeddings", "matching"
    private String dataType; // STRING, INTEGER, DOUBLE, BOOLEAN, JSON
    private String value;
    private String description;

    private boolean active;
    private Integer priority; // For overriding default configs

    // Validation
    private String validationRule; // e.g., "min:0,max:100"
    private String defaultValue;

    // Metadata
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    // Audit
    private Map<String, Object> metadata;

    // Helper methods
    public Integer getIntValue() {
        return value != null ? Integer.parseInt(value) : null;
    }

    public Double getDoubleValue() {
        return value != null ? Double.parseDouble(value) : null;
    }

    public Boolean getBooleanValue() {
        return value != null ? Boolean.parseBoolean(value) : null;
    }
}
