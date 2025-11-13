package com.tradefinance.templateextraction.service;

import com.tradefinance.templateextraction.model.mongo.SwiftMessageDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for extracting field patterns from SWIFT messages
 * Creates structural feature vectors based on field presence/absence
 */
@Service
@Slf4j
public class FieldPatternService {

    // Common SWIFT field tags across different message types
    private static final List<String> COMMON_FIELD_TAGS = Arrays.asList(
            "20", "21", "23", "23B", "23E",
            "26E", "31C", "31D", "31E",
            "32A", "32B", "32K", "33B",
            "39A", "39B", "39C",
            "40A", "40E", "41A", "41D",
            "42A", "42C", "42D", "42M", "42P",
            "43P", "43T", "44A", "44B", "44C", "44D", "44E", "44F",
            "45A", "45B", "46A", "46B", "47A", "47B",
            "48", "49", "50", "50B", "50K",
            "51A", "51D", "52A", "52D",
            "53A", "53D", "57A", "57B", "57D",
            "59", "59A", "70", "71A", "71B", "71D",
            "72", "73", "77A", "77B", "77D",
            "78", "79", "94A"
    );

    /**
     * Extract SWIFT field tags and their content from message
     */
    public Map<String, String> extractFieldsWithContent(String rawContent) {
        Map<String, String> fields = new LinkedHashMap<>();

        // Pattern to match SWIFT field tags and capture content until next field or end
        Pattern fieldPattern = Pattern.compile(":([0-9]{2}[A-Z]?):(.*?)(?=\n:|$)", Pattern.DOTALL);
        Matcher matcher = fieldPattern.matcher(rawContent);

        while (matcher.find()) {
            String fieldTag = matcher.group(1);
            String fieldValue = matcher.group(2).trim();
            fields.put(fieldTag, fieldValue);
        }

        return fields;
    }

    /**
     * Extract SWIFT field tags from message content
     */
    public Set<String> extractFieldTags(String rawContent) {
        return extractFieldsWithContent(rawContent).keySet();
    }

    /**
     * Generate a feature vector based on field presence AND content patterns
     * Returns a float array where each position represents field presence and content features
     */
    public float[] generateFieldPatternVector(SwiftMessageDocument message) {
        Map<String, String> fieldsWithContent = extractFieldsWithContent(message.getRawContent());

        // Critical fields for content-based features
        List<String> criticalFields = Arrays.asList("20", "32B", "50K", "59", "71B", "45A");
        int contentFeaturesPerField = 3; // prefix hash, content type, length bucket

        // Vector size: field presence + content features for critical fields + buyer/supplier encoding
        int vectorSize = COMMON_FIELD_TAGS.size() +
                        (criticalFields.size() * contentFeaturesPerField) +
                        20;
        float[] vector = new float[vectorSize];

        // Set field presence features
        for (int i = 0; i < COMMON_FIELD_TAGS.size(); i++) {
            String fieldTag = COMMON_FIELD_TAGS.get(i);
            vector[i] = fieldsWithContent.containsKey(fieldTag) ? 1.0f : 0.0f;
        }

        // Add content-based features for critical fields
        int contentOffset = COMMON_FIELD_TAGS.size();
        for (int i = 0; i < criticalFields.size(); i++) {
            String fieldTag = criticalFields.get(i);
            int baseIdx = contentOffset + (i * contentFeaturesPerField);

            if (fieldsWithContent.containsKey(fieldTag)) {
                String content = fieldsWithContent.get(fieldTag);

                // Feature 1: Prefix hash (first 3 characters normalized)
                String prefix = content.length() >= 3 ? content.substring(0, 3) : content;
                vector[baseIdx] = (Math.abs(prefix.hashCode()) % 100) / 100.0f;

                // Feature 2: Content type indicator (0.0 = numeric, 0.5 = mixed, 1.0 = alpha)
                vector[baseIdx + 1] = calculateContentType(content);

                // Feature 3: Length bucket (normalized)
                vector[baseIdx + 2] = Math.min(content.length() / 100.0f, 1.0f);
            } else {
                // Field not present - all zeros
                vector[baseIdx] = 0.0f;
                vector[baseIdx + 1] = 0.0f;
                vector[baseIdx + 2] = 0.0f;
            }
        }

        // Encode buyer/supplier information in the remaining dimensions
        int partyOffset = contentOffset + (criticalFields.size() * contentFeaturesPerField);
        if (message.getSenderId() != null && message.getReceiverId() != null) {
            int senderHash = Math.abs(message.getSenderId().hashCode());
            int receiverHash = Math.abs(message.getReceiverId().hashCode());

            // Distribute hash values across remaining dimensions (normalized)
            for (int i = 0; i < 10; i++) {
                int idx = partyOffset + i;
                vector[idx] = ((senderHash >> (i * 3)) & 0x7) / 7.0f;
            }

            for (int i = 0; i < 10; i++) {
                int idx = partyOffset + 10 + i;
                vector[idx] = ((receiverHash >> (i * 3)) & 0x7) / 7.0f;
            }
        }

        return vector;
    }

    /**
     * Calculate content type indicator for a field value
     * 0.0 = all numeric, 0.5 = mixed, 1.0 = all alphabetic
     */
    private float calculateContentType(String content) {
        if (content == null || content.isEmpty()) {
            return 0.5f;
        }

        long numericCount = content.chars().filter(Character::isDigit).count();
        long alphaCount = content.chars().filter(Character::isLetter).count();
        long totalRelevant = numericCount + alphaCount;

        if (totalRelevant == 0) {
            return 0.5f; // All special characters
        }

        // Return ratio: 0.0 (all numeric) to 1.0 (all alpha)
        return (float) alphaCount / totalRelevant;
    }

    /**
     * Generate field pattern vectors for multiple messages
     */
    public Map<String, float[]> generateFieldPatternVectors(List<SwiftMessageDocument> messages) {
        Map<String, float[]> vectors = new HashMap<>();

        for (SwiftMessageDocument message : messages) {
            float[] vector = generateFieldPatternVector(message);
            vectors.put(message.getId(), vector);
        }

        log.info("Generated field pattern vectors for {} messages", messages.size());
        return vectors;
    }

    /**
     * Get human-readable field pattern description
     */
    public String describeFieldPattern(SwiftMessageDocument message) {
        Set<String> fields = extractFieldTags(message.getRawContent());
        List<String> sortedFields = new ArrayList<>(fields);
        Collections.sort(sortedFields);

        return String.format("Message %s (%s): Fields [%s], %s → %s",
                message.getMessageType(),
                message.getId().substring(0, 8),
                String.join(", ", sortedFields),
                message.getSenderId(),
                message.getReceiverId());
    }

    /**
     * Calculate similarity between two field patterns
     */
    public double calculateFieldPatternSimilarity(float[] pattern1, float[] pattern2) {
        if (pattern1.length != pattern2.length) {
            throw new IllegalArgumentException("Pattern vectors must have same length");
        }

        // Calculate cosine similarity
        double dotProduct = 0.0;
        double magnitude1 = 0.0;
        double magnitude2 = 0.0;

        for (int i = 0; i < pattern1.length; i++) {
            dotProduct += pattern1[i] * pattern2[i];
            magnitude1 += pattern1[i] * pattern1[i];
            magnitude2 += pattern2[i] * pattern2[i];
        }

        magnitude1 = Math.sqrt(magnitude1);
        magnitude2 = Math.sqrt(magnitude2);

        if (magnitude1 == 0.0 || magnitude2 == 0.0) {
            return 0.0;
        }

        return dotProduct / (magnitude1 * magnitude2);
    }
}
