package com.tradefinance.templateextraction.service;

import com.tradefinance.templateextraction.model.mongo.SystemConfigurationDocument;
import com.tradefinance.templateextraction.repository.mongo.SystemConfigurationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing database-driven configuration
 * Replaces static configuration from application.yml
 */
@Service
@Slf4j
public class ConfigurationService {

    private final SystemConfigurationRepository configRepository;

    public ConfigurationService(SystemConfigurationRepository configRepository) {
        this.configRepository = configRepository;
    }

    /**
     * Get configuration value by key with fallback to default
     */
    @Cacheable(value = "config", key = "#configKey")
    public String getString(String configKey, String defaultValue) {
        return configRepository.findActiveByKey(configKey)
                .map(SystemConfigurationDocument::getValue)
                .orElse(defaultValue);
    }

    public Integer getInt(String configKey, Integer defaultValue) {
        return configRepository.findActiveByKey(configKey)
                .map(SystemConfigurationDocument::getIntValue)
                .orElse(defaultValue);
    }

    public Double getDouble(String configKey, Double defaultValue) {
        return configRepository.findActiveByKey(configKey)
                .map(SystemConfigurationDocument::getDoubleValue)
                .orElse(defaultValue);
    }

    public Boolean getBoolean(String configKey, Boolean defaultValue) {
        return configRepository.findActiveByKey(configKey)
                .map(SystemConfigurationDocument::getBooleanValue)
                .orElse(defaultValue);
    }

    /**
     * Get all configurations for a group
     */
    @Cacheable(value = "configGroup", key = "#configGroup")
    public Map<String, String> getConfigGroup(String configGroup) {
        List<SystemConfigurationDocument> configs = configRepository.findActiveByGroup(configGroup);

        Map<String, String> result = new HashMap<>();
        configs.forEach(config -> result.put(config.getConfigKey(), config.getValue()));

        return result;
    }

    /**
     * Update configuration
     */
    public SystemConfigurationDocument updateConfig(String configKey, String value, String updatedBy) {
        Optional<SystemConfigurationDocument> existing = configRepository.findByConfigKey(configKey);

        SystemConfigurationDocument config;
        if (existing.isPresent()) {
            config = existing.get();
            config.setValue(value);
            config.setUpdatedAt(LocalDateTime.now());
            config.setUpdatedBy(updatedBy);
        } else {
            log.warn("Configuration key not found: {}", configKey);
            return null;
        }

        return configRepository.save(config);
    }

    /**
     * Create new configuration
     */
    public SystemConfigurationDocument createConfig(String configKey, String configGroup,
                                                    String value, String dataType,
                                                    String description, String createdBy) {
        SystemConfigurationDocument config = SystemConfigurationDocument.builder()
                .configKey(configKey)
                .configGroup(configGroup)
                .value(value)
                .dataType(dataType)
                .description(description)
                .active(true)
                .priority(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy(createdBy)
                .build();

        return configRepository.save(config);
    }

    /**
     * Initialize default configurations if not present
     */
    public void initializeDefaultConfigurations() {
        log.info("Initializing default configurations");

        // Clustering configurations
        createIfNotExists("clustering.maxIterations", "clustering", "100", "INTEGER",
                "Maximum iterations for K-Means clustering", "system");
        createIfNotExists("clustering.minClusters", "clustering", "2", "INTEGER",
                "Minimum number of clusters", "system");
        createIfNotExists("clustering.maxClusters", "clustering", "10", "INTEGER",
                "Maximum number of clusters", "system");
        createIfNotExists("clustering.convergenceThreshold", "clustering", "0.001", "DOUBLE",
                "Convergence threshold for clustering", "system");

        // Embeddings configurations
        createIfNotExists("embeddings.modelName", "embeddings",
                "sentence-transformers/all-MiniLM-L6-v2", "STRING",
                "Name of the embedding model", "system");
        createIfNotExists("embeddings.dimension", "embeddings", "384", "INTEGER",
                "Dimension of embeddings", "system");
        createIfNotExists("embeddings.cacheSize", "embeddings", "10000", "INTEGER",
                "Embedding cache size", "system");

        // Similarity configurations
        createIfNotExists("similarity.threshold", "similarity", "0.85", "DOUBLE",
                "Minimum similarity threshold for matching", "system");
        createIfNotExists("similarity.autoApproveThreshold", "similarity", "0.95", "DOUBLE",
                "Auto-approve threshold for high confidence matches", "system");

        // Template configurations
        createIfNotExists("template.minMessagesForTemplate", "template", "3", "INTEGER",
                "Minimum messages required to create a template", "system");
        createIfNotExists("template.autoGenerate", "template", "true", "BOOLEAN",
                "Automatically generate templates", "system");

        log.info("Default configurations initialized");
    }

    private void createIfNotExists(String configKey, String configGroup, String value,
                                  String dataType, String description, String createdBy) {
        if (configRepository.findByConfigKey(configKey).isEmpty()) {
            createConfig(configKey, configGroup, value, dataType, description, createdBy);
            log.info("Created default configuration: {}", configKey);
        }
    }

    /**
     * Get all active configurations
     */
    public List<SystemConfigurationDocument> getAllActiveConfigurations() {
        return configRepository.findAllActive();
    }

    /**
     * Toggle configuration active status
     */
    public void toggleConfigStatus(String configKey, boolean active) {
        configRepository.findByConfigKey(configKey).ifPresent(config -> {
            config.setActive(active);
            config.setUpdatedAt(LocalDateTime.now());
            configRepository.save(config);
            log.info("Configuration {} set to active={}", configKey, active);
        });
    }
}
