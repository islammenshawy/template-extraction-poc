package com.tradefinance.templateextraction.service;

import com.tradefinance.templateextraction.model.VectorEmbedding;
import com.tradefinance.templateextraction.repository.VectorEmbeddingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing vector embeddings in ElasticSearch
 * Handles all vector similarity operations
 */
@Service
@Slf4j
public class VectorService {

    private final VectorEmbeddingRepository vectorRepository;
    private final EmbeddingService embeddingService;

    public VectorService(VectorEmbeddingRepository vectorRepository,
                        EmbeddingService embeddingService) {
        this.vectorRepository = vectorRepository;
        this.embeddingService = embeddingService;
    }

    /**
     * Store a vector embedding for a document
     */
    public VectorEmbedding storeVector(String documentId, String documentType, String content, Integer clusterId) {
        float[] embedding = embeddingService.generateEmbedding(content);

        VectorEmbedding vector = VectorEmbedding.builder()
                .id(documentId)
                .documentType(documentType)
                .referenceId(documentId)
                .embedding(embedding)
                .clusterId(clusterId)
                .contentPreview(content.substring(0, Math.min(200, content.length())))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return vectorRepository.save(vector);
    }

    /**
     * Update an existing vector embedding
     */
    public VectorEmbedding updateVector(String documentId, String content, Integer clusterId) {
        Optional<VectorEmbedding> existing = vectorRepository.findById(documentId);

        if (existing.isEmpty()) {
            log.warn("Vector not found for document: {}", documentId);
            return null;
        }

        VectorEmbedding vector = existing.get();
        vector.setEmbedding(embeddingService.generateEmbedding(content));
        vector.setClusterId(clusterId);
        vector.setContentPreview(content.substring(0, Math.min(200, content.length())));
        vector.setUpdatedAt(LocalDateTime.now());

        return vectorRepository.save(vector);
    }

    /**
     * Get vector by document ID
     */
    public Optional<VectorEmbedding> getVector(String documentId) {
        return vectorRepository.findById(documentId);
    }

    /**
     * Delete vector by document ID
     */
    public void deleteVector(String documentId) {
        vectorRepository.deleteById(documentId);
        log.info("Deleted vector for document: {}", documentId);
    }

    /**
     * Find similar vectors using cosine similarity
     */
    public List<SimilarityResult> findSimilarVectors(String documentId, String documentType, int topK) {
        Optional<VectorEmbedding> queryVector = vectorRepository.findById(documentId);

        if (queryVector.isEmpty()) {
            log.warn("Query vector not found: {}", documentId);
            return Collections.emptyList();
        }

        return findSimilarVectors(queryVector.get().getEmbedding(), documentType, topK);
    }

    /**
     * Find similar vectors using a given embedding
     */
    public List<SimilarityResult> findSimilarVectors(float[] queryEmbedding, String documentType, int topK) {
        List<VectorEmbedding> candidates = vectorRepository.findByDocumentType(documentType);

        return candidates.stream()
                .map(vector -> {
                    double similarity = embeddingService.cosineSimilarity(queryEmbedding, vector.getEmbedding());
                    return new SimilarityResult(vector.getReferenceId(), similarity, vector);
                })
                .sorted(Comparator.comparingDouble(SimilarityResult::getSimilarity).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    /**
     * Find vectors by cluster
     */
    public List<VectorEmbedding> getVectorsByCluster(Integer clusterId) {
        return vectorRepository.findByClusterId(clusterId);
    }

    /**
     * Calculate centroid embedding for a cluster
     */
    public float[] calculateClusterCentroid(Integer clusterId) {
        List<VectorEmbedding> clusterVectors = vectorRepository.findByClusterId(clusterId);

        if (clusterVectors.isEmpty()) {
            return new float[384]; // Empty embedding
        }

        List<float[]> embeddings = clusterVectors.stream()
                .map(VectorEmbedding::getEmbedding)
                .collect(Collectors.toList());

        return embeddingService.calculateCentroid(embeddings);
    }

    /**
     * Store centroid for a template
     */
    public VectorEmbedding storeCentroid(String templateId, Integer clusterId) {
        float[] centroid = calculateClusterCentroid(clusterId);

        // Check if centroid has non-zero magnitude (ElasticSearch with cosine similarity doesn't support zero vectors)
        boolean isZeroVector = true;
        for (float value : centroid) {
            if (value != 0.0f) {
                isZeroVector = false;
                break;
            }
        }

        if (isZeroVector) {
            log.warn("Centroid for cluster {} has zero magnitude, skipping vector storage", clusterId);
            return null;
        }

        VectorEmbedding vector = VectorEmbedding.builder()
                .id(templateId)
                .documentType(VectorEmbedding.DocumentType.TEMPLATE.name())
                .referenceId(templateId)
                .embedding(centroid)
                .clusterId(clusterId)
                .contentPreview("Template centroid for cluster " + clusterId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return vectorRepository.save(vector);
    }

    /**
     * Get all vectors for a document type
     */
    public List<VectorEmbedding> getVectorsByType(String documentType) {
        return vectorRepository.findByDocumentType(documentType);
    }

    /**
     * Get statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        long totalVectors = vectorRepository.count();
        stats.put("totalVectors", totalVectors);

        long messageVectors = vectorRepository.countByDocumentType(VectorEmbedding.DocumentType.MESSAGE.name());
        long templateVectors = vectorRepository.countByDocumentType(VectorEmbedding.DocumentType.TEMPLATE.name());

        stats.put("messageVectors", messageVectors);
        stats.put("templateVectors", templateVectors);

        return stats;
    }

    /**
     * Similarity result class
     */
    public static class SimilarityResult {
        private final String documentId;
        private final double similarity;
        private final VectorEmbedding vector;

        public SimilarityResult(String documentId, double similarity, VectorEmbedding vector) {
            this.documentId = documentId;
            this.similarity = similarity;
            this.vector = vector;
        }

        public String getDocumentId() {
            return documentId;
        }

        public double getSimilarity() {
            return similarity;
        }

        public VectorEmbedding getVector() {
            return vector;
        }
    }
}
