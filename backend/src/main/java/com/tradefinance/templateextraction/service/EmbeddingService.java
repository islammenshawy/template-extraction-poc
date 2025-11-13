package com.tradefinance.templateextraction.service;

import ai.djl.Application;
import ai.djl.MalformedModelException;
import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.TranslateException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class EmbeddingService {

    @Value("${template.extraction.embeddings.model-name:sentence-transformers/all-MiniLM-L6-v2}")
    private String modelName;

    @Value("${template.extraction.embeddings.dimension:384}")
    private int embeddingDimension;

    @Value("${template.extraction.embeddings.cache-size:1000}")
    private int cacheSize;

    private final Map<String, float[]> embeddingCache = new ConcurrentHashMap<>();
    private ZooModel<String, float[]> model;
    private Predictor<String, float[]> predictor;
    private boolean modelLoaded = false;

    @PostConstruct
    public void initialize() {
        log.info("Initializing Sentence-BERT embedding service with model: {}", modelName);

        try {
            // Build criteria for Sentence-BERT model
            Criteria<String, float[]> criteria = Criteria.builder()
                    .optApplication(Application.NLP.TEXT_EMBEDDING)
                    .setTypes(String.class, float[].class)
                    .optModelUrls("djl://ai.djl.huggingface.pytorch/" + modelName)
                    .optEngine("PyTorch")
                    .optProgress(new ProgressBar())
                    .build();

            log.info("Loading Sentence-BERT model from HuggingFace...");
            model = criteria.loadModel();
            predictor = model.newPredictor();
            modelLoaded = true;

            log.info("✓ Sentence-BERT model loaded successfully! Dimension: {}", embeddingDimension);

            // Test embedding
            float[] testEmbedding = generateEmbedding("test");
            log.info("✓ Test embedding generated successfully with dimension: {}", testEmbedding.length);

        } catch (ModelNotFoundException | MalformedModelException | IOException e) {
            log.error("Failed to load Sentence-BERT model. Falling back to simple embeddings.", e);
            log.warn("For production use, ensure model can be downloaded or is cached.");
            modelLoaded = false;
        }
    }

    @PreDestroy
    public void cleanup() {
        if (predictor != null) {
            predictor.close();
        }
        if (model != null) {
            model.close();
        }
        embeddingCache.clear();
        log.info("Embedding service cleaned up");
    }

    /**
     * Generate embedding for a given text using Sentence-BERT
     */
    public float[] generateEmbedding(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new float[embeddingDimension];
        }

        // Check cache first
        String cacheKey = text.length() > 100 ? text.substring(0, 100) : text;
        if (embeddingCache.containsKey(cacheKey)) {
            return embeddingCache.get(cacheKey);
        }

        try {
            float[] embedding;

            if (modelLoaded && predictor != null) {
                // Use actual Sentence-BERT model
                embedding = predictor.predict(text);

                // Normalize embedding to unit length
                embedding = normalize(embedding);
            } else {
                // Fallback to simple embeddings if model not loaded
                embedding = generateSimpleEmbedding(text);
            }

            // Cache if within limit
            if (embeddingCache.size() < cacheSize) {
                embeddingCache.put(cacheKey, embedding);
            }

            return embedding;
        } catch (TranslateException e) {
            log.error("Error generating embedding with Sentence-BERT, falling back to simple method", e);
            return generateSimpleEmbedding(text);
        } catch (Exception e) {
            log.error("Error generating embedding for text: {}", text.substring(0, Math.min(50, text.length())), e);
            return new float[embeddingDimension];
        }
    }

    /**
     * Simple embedding generation fallback (for POC or when model fails to load)
     */
    private float[] generateSimpleEmbedding(String text) {
        float[] embedding = new float[embeddingDimension];

        // Normalize text
        String normalized = text.toLowerCase().replaceAll("[^a-z0-9\\s]", " ");
        String[] words = normalized.split("\\s+");

        // Generate pseudo-embedding based on word features
        for (int i = 0; i < embeddingDimension; i++) {
            float value = 0.0f;
            for (String word : words) {
                if (!word.isEmpty()) {
                    // Use word hash and position to generate deterministic values
                    int wordHash = (word.hashCode() + i) % 1000;
                    value += Math.sin(wordHash / 100.0);
                }
            }
            embedding[i] = (float) (value / Math.sqrt(words.length + 1));
        }

        // Normalize embedding
        return normalize(embedding);
    }

    /**
     * Calculate cosine similarity between two embeddings
     */
    public double cosineSimilarity(float[] vec1, float[] vec2) {
        if (vec1 == null || vec2 == null || vec1.length != vec2.length) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }

        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * Normalize a vector to unit length
     */
    private float[] normalize(float[] vector) {
        double norm = 0.0;
        for (float v : vector) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);

        if (norm == 0.0) {
            return vector;
        }

        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = (float) (vector[i] / norm);
        }
        return normalized;
    }

    /**
     * Calculate centroid of multiple embeddings
     */
    public float[] calculateCentroid(List<float[]> embeddings) {
        if (embeddings == null || embeddings.isEmpty()) {
            return new float[embeddingDimension];
        }

        float[] centroid = new float[embeddingDimension];
        for (float[] embedding : embeddings) {
            for (int i = 0; i < embeddingDimension; i++) {
                centroid[i] += embedding[i];
            }
        }

        // Average
        for (int i = 0; i < embeddingDimension; i++) {
            centroid[i] /= embeddings.size();
        }

        return normalize(centroid);
    }

    /**
     * Calculate batch embeddings (more efficient for multiple texts)
     */
    public Map<String, float[]> generateBatchEmbeddings(List<String> texts) {
        Map<String, float[]> embeddings = new HashMap<>();

        for (String text : texts) {
            embeddings.put(text, generateEmbedding(text));
        }

        return embeddings;
    }

    /**
     * Check if model is loaded and ready
     */
    public boolean isModelLoaded() {
        return modelLoaded;
    }

    /**
     * Get embedding dimension
     */
    public int getEmbeddingDimension() {
        return embeddingDimension;
    }

    /**
     * Clear embedding cache
     */
    public void clearCache() {
        embeddingCache.clear();
        log.info("Embedding cache cleared");
    }

    /**
     * Get cache size
     */
    public int getCacheSize() {
        return embeddingCache.size();
    }

    /**
     * Extract non-variable content from a field value
     * Removes placeholders like {VARIABLE}, [VARIABLE], <VARIABLE>, etc.
     */
    public String extractNonVariableContent(String fieldValue) {
        if (fieldValue == null || fieldValue.trim().isEmpty()) {
            return "";
        }

        // Remove common variable placeholders
        String cleaned = fieldValue
                .replaceAll("\\{[^}]*\\}", "")  // Remove {VARIABLE}
                .replaceAll("\\[[^\\]]*\\]", "")  // Remove [VARIABLE]
                .replaceAll("<[^>]*>", "")  // Remove <VARIABLE>
                .replaceAll("\\$\\{[^}]*\\}", "")  // Remove ${VARIABLE}
                .replaceAll("\\d{6,}", "")  // Remove long numbers (likely dates/amounts)
                .trim();

        // Normalize whitespace
        return cleaned.replaceAll("\\s+", " ").trim();
    }

    /**
     * Calculate Levenshtein distance between two strings
     */
    public int calculateLevenshteinDistance(String s1, String s2) {
        if (s1 == null) s1 = "";
        if (s2 == null) s2 = "";

        int len1 = s1.length();
        int len2 = s2.length();

        int[][] dp = new int[len1 + 1][len2 + 1];

        for (int i = 0; i <= len1; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= len2; j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[len1][len2];
    }

    /**
     * Calculate similarity score (0.0 to 1.0) based on Levenshtein distance
     */
    public double calculateTextSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return 0.0;
        }

        // Normalize strings (lowercase, trim)
        s1 = s1.toLowerCase().trim();
        s2 = s2.toLowerCase().trim();

        if (s1.isEmpty() && s2.isEmpty()) {
            return 1.0;
        }
        if (s1.isEmpty() || s2.isEmpty()) {
            return 0.0;
        }

        int distance = calculateLevenshteinDistance(s1, s2);
        int maxLength = Math.max(s1.length(), s2.length());

        return 1.0 - ((double) distance / maxLength);
    }

    /**
     * Calculate comprehensive field similarity score combining multiple factors
     *
     * @param templateFieldValue The template field value (may contain placeholders)
     * @param messageFieldValue The actual message field value
     * @return Similarity score between 0.0 and 1.0
     */
    public double calculateFieldSimilarity(String templateFieldValue, String messageFieldValue) {
        if (templateFieldValue == null || messageFieldValue == null) {
            return 0.0;
        }

        // Extract non-variable content from template
        String templateContent = extractNonVariableContent(templateFieldValue);
        String messageContent = messageFieldValue.trim();

        // If template has no fixed content (all variables), give high confidence
        if (templateContent.isEmpty()) {
            return 0.95; // Variable-only field
        }

        // Calculate text similarity (Levenshtein-based)
        double textSim = calculateTextSimilarity(templateContent, messageContent);

        // Calculate semantic similarity using embeddings
        double semanticSim = 0.0;
        try {
            if (!templateContent.isEmpty() && !messageContent.isEmpty()) {
                float[] templateEmb = generateEmbedding(templateContent);
                float[] messageEmb = generateEmbedding(messageContent);
                semanticSim = cosineSimilarity(templateEmb, messageEmb);
            }
        } catch (Exception e) {
            log.debug("Could not calculate semantic similarity, using text similarity only", e);
            semanticSim = textSim;
        }

        // Weighted combination: 60% semantic, 40% text similarity
        double combinedScore = (0.6 * semanticSim) + (0.4 * textSim);

        // Ensure score is between 0 and 1
        return Math.max(0.0, Math.min(1.0, combinedScore));
    }
}
