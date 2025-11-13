package com.tradefinance.templateextraction.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.ml.clustering.CentroidCluster;
import org.apache.commons.math3.ml.clustering.Clusterable;
import org.apache.commons.math3.ml.clustering.KMeansPlusPlusClusterer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ClusteringService {

    @Value("${template.extraction.clustering.max-iterations}")
    private int maxIterations;

    @Value("${template.extraction.clustering.min-clusters}")
    private int minClusters;

    @Value("${template.extraction.clustering.max-clusters}")
    private int maxClusters;

    @Value("${template.extraction.clustering.convergence-threshold}")
    private double convergenceThreshold;

    private final EmbeddingService embeddingService;

    public ClusteringService(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    /**
     * Cluster messages using K-Means algorithm
     */
    public Map<Integer, List<String>> clusterMessages(Map<String, float[]> messageEmbeddings) {
        if (messageEmbeddings.isEmpty()) {
            return new HashMap<>();
        }

        // Determine optimal number of clusters
        int optimalK = determineOptimalClusters(messageEmbeddings.values());
        log.info("Optimal number of clusters determined: {}", optimalK);

        // Convert to Clusterable objects
        List<MessagePoint> points = messageEmbeddings.entrySet().stream()
                .map(entry -> new MessagePoint(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        // Perform K-Means clustering
        KMeansPlusPlusClusterer<MessagePoint> clusterer = new KMeansPlusPlusClusterer<>(
                optimalK, maxIterations);

        List<CentroidCluster<MessagePoint>> clusters = clusterer.cluster(points);

        // Convert to result map
        Map<Integer, List<String>> result = new HashMap<>();
        for (int i = 0; i < clusters.size(); i++) {
            List<String> messageIds = clusters.get(i).getPoints().stream()
                    .map(MessagePoint::getMessageId)
                    .collect(Collectors.toList());
            result.put(i, messageIds);
        }

        log.info("Clustered {} messages into {} clusters", messageEmbeddings.size(), result.size());
        return result;
    }

    /**
     * Determine optimal number of clusters using elbow method
     */
    private int determineOptimalClusters(Collection<float[]> embeddings) {
        if (embeddings.size() < minClusters) {
            return Math.max(1, embeddings.size());
        }

        List<MessagePoint> points = new ArrayList<>();
        int idx = 0;
        for (float[] embedding : embeddings) {
            points.add(new MessagePoint("temp_" + idx++, embedding));
        }

        double bestScore = Double.MAX_VALUE;
        int optimalK = minClusters;

        // Try different k values and calculate inertia
        for (int k = minClusters; k <= Math.min(maxClusters, points.size()); k++) {
            try {
                KMeansPlusPlusClusterer<MessagePoint> clusterer = new KMeansPlusPlusClusterer<>(k, maxIterations);
                List<CentroidCluster<MessagePoint>> clusters = clusterer.cluster(points);

                double inertia = calculateInertia(clusters);
                double score = inertia / k; // Normalized score

                if (score < bestScore * (1 - convergenceThreshold)) {
                    bestScore = score;
                    optimalK = k;
                }
            } catch (Exception e) {
                log.warn("Error clustering with k={}: {}", k, e.getMessage());
            }
        }

        return optimalK;
    }

    /**
     * Calculate inertia (sum of squared distances to centroids)
     */
    private double calculateInertia(List<CentroidCluster<MessagePoint>> clusters) {
        double inertia = 0.0;
        for (CentroidCluster<MessagePoint> cluster : clusters) {
            double[] centroid = cluster.getCenter().getPoint();
            for (MessagePoint point : cluster.getPoints()) {
                inertia += squaredDistance(point.getPoint(), centroid);
            }
        }
        return inertia;
    }

    /**
     * Calculate squared Euclidean distance
     */
    private double squaredDistance(double[] p1, double[] p2) {
        double sum = 0.0;
        for (int i = 0; i < p1.length; i++) {
            double diff = p1[i] - p2[i];
            sum += diff * diff;
        }
        return sum;
    }

    /**
     * Find the closest cluster for a given embedding
     */
    public int findClosestCluster(float[] embedding, Map<Integer, float[]> clusterCentroids) {
        if (clusterCentroids.isEmpty()) {
            return -1;
        }

        int closestCluster = -1;
        double maxSimilarity = -1.0;

        for (Map.Entry<Integer, float[]> entry : clusterCentroids.entrySet()) {
            double similarity = embeddingService.cosineSimilarity(embedding, entry.getValue());
            if (similarity > maxSimilarity) {
                maxSimilarity = similarity;
                closestCluster = entry.getKey();
            }
        }

        return closestCluster;
    }

    /**
     * Clusterable implementation for messages
     */
    @Data
    @AllArgsConstructor
    private static class MessagePoint implements Clusterable {
        private String messageId;
        private float[] embedding;

        @Override
        public double[] getPoint() {
            double[] point = new double[embedding.length];
            for (int i = 0; i < embedding.length; i++) {
                point[i] = embedding[i];
            }
            return point;
        }
    }
}
