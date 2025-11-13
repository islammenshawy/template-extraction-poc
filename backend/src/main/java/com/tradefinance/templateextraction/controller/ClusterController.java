package com.tradefinance.templateextraction.controller;

import com.tradefinance.templateextraction.model.VectorEmbedding;
import com.tradefinance.templateextraction.model.mongo.SwiftMessageDocument;
import com.tradefinance.templateextraction.repository.VectorEmbeddingRepository;
import com.tradefinance.templateextraction.repository.mongo.SwiftMessageMongoRepository;
import com.tradefinance.templateextraction.service.ClusteringService;
import com.tradefinance.templateextraction.util.DimensionalityReduction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/clusters")
@CrossOrigin(originPatterns = "*")
@Slf4j
public class ClusterController {

    private final VectorEmbeddingRepository vectorRepository;
    private final SwiftMessageMongoRepository messageRepository;
    private final ClusteringService clusteringService;

    public ClusterController(VectorEmbeddingRepository vectorRepository,
                            SwiftMessageMongoRepository messageRepository,
                            ClusteringService clusteringService) {
        this.vectorRepository = vectorRepository;
        this.messageRepository = messageRepository;
        this.clusteringService = clusteringService;
    }

    @GetMapping("/visualize")
    public ResponseEntity<Map<String, Object>> getClusterVisualization(
            @RequestParam(defaultValue = "4") int numClusters) {

        log.info("Generating cluster visualization with {} clusters", numClusters);

        try {
            // Get all message vectors from ElasticSearch
            List<VectorEmbedding> vectors = vectorRepository.findByDocumentType("MESSAGE");

            if (vectors.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                    "error", "No vectors found",
                    "message", "Please upload messages first"
                ));
            }

            // Get message metadata from MongoDB
            List<String> messageIds = vectors.stream()
                    .map(VectorEmbedding::getReferenceId)
                    .collect(Collectors.toList());

            List<SwiftMessageDocument> messages = messageRepository.findAllById(messageIds);
            Map<String, SwiftMessageDocument> messageMap = messages.stream()
                    .collect(Collectors.toMap(SwiftMessageDocument::getId, m -> m));

            // Perform K-Means clustering
            Map<String, float[]> embeddingMap = vectors.stream()
                    .collect(Collectors.toMap(
                            VectorEmbedding::getId,
                            VectorEmbedding::getEmbedding
                    ));

            Map<Integer, List<String>> clusters = clusteringService.clusterMessages(embeddingMap);

            // Calculate 2D projections for visualization
            float[][] embeddings = vectors.stream()
                    .map(VectorEmbedding::getEmbedding)
                    .toArray(float[][]::new);

            double[][] projections2D = DimensionalityReduction.reduce2D(embeddings);

            // Map vector IDs to 2D coordinates
            Map<String, double[]> coordinatesMap = new HashMap<>();
            for (int i = 0; i < vectors.size(); i++) {
                coordinatesMap.put(vectors.get(i).getId(), projections2D[i]);
            }

            // Prepare response data
            List<Map<String, Object>> clusterData = new ArrayList<>();

            for (Map.Entry<Integer, List<String>> entry : clusters.entrySet()) {
                Integer clusterId = entry.getKey();
                List<String> vectorIds = entry.getValue();

                // Get messages in this cluster
                List<Map<String, Object>> clusterMessages = new ArrayList<>();
                Map<String, Integer> typeCount = new HashMap<>();
                Map<String, Integer> senderCount = new HashMap<>();

                for (String vectorId : vectorIds) {
                    VectorEmbedding vector = vectors.stream()
                            .filter(v -> v.getId().equals(vectorId))
                            .findFirst()
                            .orElse(null);

                    if (vector != null) {
                        SwiftMessageDocument msg = messageMap.get(vector.getReferenceId());
                        if (msg != null) {
                            Map<String, Object> msgData = new HashMap<>();
                            msgData.put("id", msg.getId());
                            msgData.put("messageType", msg.getMessageType());
                            msgData.put("senderId", msg.getSenderId());
                            msgData.put("receiverId", msg.getReceiverId());
                            msgData.put("status", msg.getStatus());
                            msgData.put("clusterId", clusterId);

                            // Get 2D projection coordinates
                            double[] coords = coordinatesMap.get(vectorId);
                            if (coords != null) {
                                msgData.put("x", coords[0]);
                                msgData.put("y", coords[1]);
                            } else {
                                msgData.put("x", 50.0);
                                msgData.put("y", 50.0);
                            }

                            clusterMessages.add(msgData);

                            // Count statistics
                            if (msg.getMessageType() != null) {
                                typeCount.put(msg.getMessageType(),
                                        typeCount.getOrDefault(msg.getMessageType(), 0) + 1);
                            }
                            if (msg.getSenderId() != null) {
                                senderCount.put(msg.getSenderId(),
                                        senderCount.getOrDefault(msg.getSenderId(), 0) + 1);
                            }
                        }
                    }
                }

                Map<String, Object> cluster = new HashMap<>();
                cluster.put("id", clusterId);
                cluster.put("size", clusterMessages.size());
                cluster.put("messages", clusterMessages);
                cluster.put("typeDistribution", typeCount);
                cluster.put("senderDistribution", senderCount);

                clusterData.add(cluster);
            }

            // Prepare overall statistics
            Map<String, Object> statistics = new HashMap<>();
            statistics.put("totalMessages", vectors.size());
            statistics.put("totalClusters", clusters.size());

            Map<String, Integer> overallTypes = new HashMap<>();
            for (SwiftMessageDocument msg : messages) {
                overallTypes.put(msg.getMessageType(),
                        overallTypes.getOrDefault(msg.getMessageType(), 0) + 1);
            }
            statistics.put("messageTypes", overallTypes);

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("clusters", clusterData);
            response.put("statistics", statistics);
            response.put("timestamp", new Date());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error generating cluster visualization", e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to generate clusters",
                "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getClusterStatistics() {
        try {
            List<VectorEmbedding> vectors = vectorRepository.findByDocumentType("MESSAGE");
            List<SwiftMessageDocument> messages = messageRepository.findAll();

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalVectors", vectors.size());
            stats.put("totalMessages", messages.size());

            // Count by message type
            Map<String, Long> typeCount = messages.stream()
                    .collect(Collectors.groupingBy(
                            SwiftMessageDocument::getMessageType,
                            Collectors.counting()
                    ));
            stats.put("messageTypes", typeCount);

            // Count by sender
            Map<String, Long> senderCount = messages.stream()
                    .collect(Collectors.groupingBy(
                            SwiftMessageDocument::getSenderId,
                            Collectors.counting()
                    ));
            stats.put("topSenders", senderCount.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(10)
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (e1, e2) -> e1,
                            LinkedHashMap::new
                    )));

            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("Error getting cluster statistics", e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to get statistics",
                "message", e.getMessage()
            ));
        }
    }
}
