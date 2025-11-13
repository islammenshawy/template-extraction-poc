package com.tradefinance.templateextraction.service;

import com.tradefinance.templateextraction.model.MessageTemplate;
import com.tradefinance.templateextraction.model.VectorEmbedding;
import com.tradefinance.templateextraction.model.mongo.SwiftMessageDocument;
import com.tradefinance.templateextraction.repository.MessageTemplateRepository;
import com.tradefinance.templateextraction.repository.mongo.SwiftMessageMongoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TemplateExtractionService {

    private final SwiftMessageMongoRepository swiftMessageRepository;
    private final MessageTemplateRepository templateRepository;
    private final EmbeddingService embeddingService;
    private final ClusteringService clusteringService;
    private final VectorService vectorService;
    private final FieldPatternService fieldPatternService;

    // Patterns for identifying variable fields
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("\\d+");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("\\d+[.,]\\d{2}");
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{2}[/-]\\d{2}[/-]\\d{4}|\\d{8}");
    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("[A-Z0-9]{10,}");

    // Quality thresholds for template extraction
    private static final int MIN_CLUSTER_SIZE = 3;  // Minimum messages to create a template
    private static final int MAX_TEMPLATES_PER_PAIR = 3;  // Maximum templates to keep per trading pair
    private static final int HIGH_VOLUME_THRESHOLD = 10;  // High volume cluster threshold
    private static final double OUTLIER_STD_DEV_THRESHOLD = 2.0;  // Standard deviations for outlier detection

    public TemplateExtractionService(SwiftMessageMongoRepository swiftMessageRepository,
                                    MessageTemplateRepository templateRepository,
                                    EmbeddingService embeddingService,
                                    ClusteringService clusteringService,
                                    VectorService vectorService,
                                    FieldPatternService fieldPatternService) {
        this.swiftMessageRepository = swiftMessageRepository;
        this.templateRepository = templateRepository;
        this.embeddingService = embeddingService;
        this.clusteringService = clusteringService;
        this.vectorService = vectorService;
        this.fieldPatternService = fieldPatternService;
    }

    /**
     * Extract templates from all unprocessed messages
     */
    public Map<String, Object> extractTemplates() {
        log.info("Starting template extraction process");

        // Get all messages that need clustering
        List<SwiftMessageDocument> messages = swiftMessageRepository.findByStatus(SwiftMessageDocument.ProcessingStatus.EMBEDDED);

        if (messages.isEmpty()) {
            log.warn("No messages available for template extraction");
            return createResponse(0, 0, Collections.emptyList());
        }

        // Group by message type, then by trading pair (buyer-seller combination)
        Map<String, List<SwiftMessageDocument>> messagesByType = messages.stream()
                .collect(Collectors.groupingBy(SwiftMessageDocument::getMessageType));

        int totalClusters = 0;
        List<Map<String, Object>> templateInfos = new ArrayList<>();

        // Process each message type separately
        for (Map.Entry<String, List<SwiftMessageDocument>> entry : messagesByType.entrySet()) {
            String messageType = entry.getKey();
            List<SwiftMessageDocument> typeMessages = entry.getValue();

            log.info("Processing {} messages of type {}", typeMessages.size(), messageType);

            // Further group by trading pair (senderId + receiverId combination)
            Map<String, List<SwiftMessageDocument>> messagesByTradingPair = typeMessages.stream()
                    .collect(Collectors.groupingBy(msg ->
                            msg.getSenderId() + ":" + msg.getReceiverId()));

            log.info("Found {} trading pairs for message type {}",
                    messagesByTradingPair.size(), messageType);

            // Process each trading pair separately
            for (Map.Entry<String, List<SwiftMessageDocument>> pairEntry : messagesByTradingPair.entrySet()) {
                String tradingPair = pairEntry.getKey();
                List<SwiftMessageDocument> pairMessages = pairEntry.getValue();

                log.info("Processing trading pair {} with {} messages",
                        tradingPair, pairMessages.size());

                // Use pairMessages instead of typeMessages for clustering
                int clustersCreated = processMessageGroup(tradingPair, messageType,
                        pairMessages, templateInfos);
                totalClusters += clustersCreated;
            }
        }

        return createResponse(messages.size(), totalClusters, templateInfos);
    }

    /**
     * Process a group of messages (from same trading pair and message type)
     * Returns the number of clusters created
     */
    private int processMessageGroup(String tradingPair, String messageType,
            List<SwiftMessageDocument> typeMessages,
            List<Map<String, Object>> templateInfos) {

        // Generate field pattern vectors for structural clustering
        Map<String, float[]> fieldPatternVectors = fieldPatternService.generateFieldPatternVectors(typeMessages);

        if (fieldPatternVectors.isEmpty()) {
            log.warn("No field patterns generated for trading pair {} (type {})", tradingPair, messageType);
            return 0;
        }

        log.info("Generated {} field pattern vectors for clustering (based on field structure + buyer/supplier)",
                fieldPatternVectors.size());

        // Generate text embeddings for semantic clustering
        Map<String, float[]> textEmbeddingVectors = new HashMap<>();
        for (SwiftMessageDocument message : typeMessages) {
            Optional<VectorEmbedding> vectorOpt = vectorService.getVector(message.getId());
            if (vectorOpt.isPresent()) {
                textEmbeddingVectors.put(message.getId(), vectorOpt.get().getEmbedding());
            } else {
                log.warn("No text embedding found for message {}, skipping from clustering", message.getId());
            }
        }

        log.info("Generated {} text embedding vectors for semantic clustering (using Sentence-BERT)",
                textEmbeddingVectors.size());

        // Combine field patterns and text embeddings for hybrid clustering
        // This allows semantic variations like "PROHIBITED" vs "NOT ALLOWED" to cluster together
        Map<String, float[]> combinedVectors = combineFeaturesForClustering(
                fieldPatternVectors, textEmbeddingVectors);

        log.info("Created {} combined feature vectors (structure + semantics) for clustering",
                combinedVectors.size());

        // Cluster messages based on combined features (structure + semantic content)
        // This identifies similar messages and filters out noise/outliers
        Map<Integer, List<String>> clusters = clusteringService.clusterMessages(combinedVectors);

        // Select TOP 3 clusters to create multiple templates per trading pair
        // This captures different message patterns while filtering noise
        List<Map.Entry<Integer, List<String>>> topClusters = selectTopClusters(
                clusters, typeMessages, combinedVectors, MAX_TEMPLATES_PER_PAIR);

        if (topClusters.isEmpty()) {
            log.warn("No suitable clusters found for trading pair {} (type {})", tradingPair, messageType);
            return 0;
        }

        log.info("Selected {} clusters out of {} total for trading pair {}",
                topClusters.size(), clusters.size(), tradingPair);

        int templatesCreated = 0;

        // Create a template for each of the top clusters
        for (Map.Entry<Integer, List<String>> cluster : topClusters) {
            Integer clusterId = cluster.getKey();
            List<String> messageIds = cluster.getValue();

            List<SwiftMessageDocument> clusterMessages = typeMessages.stream()
                    .filter(m -> messageIds.contains(m.getId()))
                    .collect(Collectors.toList());

            // Skip clusters that are too small
            if (clusterMessages.size() < MIN_CLUSTER_SIZE) {
                log.info("Skipping cluster {} - only {} messages (minimum: {})",
                        clusterId, clusterMessages.size(), MIN_CLUSTER_SIZE);
                continue;
            }

            log.info("Processing cluster {} with {} messages out of {} total for trading pair {}",
                    clusterId, clusterMessages.size(), typeMessages.size(), tradingPair);

            // Filter out outliers before template extraction (using combined vectors)
            List<SwiftMessageDocument> filteredMessages = filterOutliers(clusterMessages, combinedVectors);
            log.info("Filtered cluster {} from {} to {} messages after outlier removal",
                    clusterId, clusterMessages.size(), filteredMessages.size());

            // Extract template using field patterns (structure only, not semantics)
            MessageTemplate template = extractTemplateFromCluster(clusterId, filteredMessages, fieldPatternVectors);
            template = templateRepository.save(template);

            // Update messages with cluster and template info
            for (SwiftMessageDocument message : clusterMessages) {
                message.setClusterId(clusterId);
                message.setTemplateId(template.getId());
                message.setStatus(SwiftMessageDocument.ProcessingStatus.CLUSTERED);
            }
            swiftMessageRepository.saveAll(clusterMessages);

            // Store template centroid in vector store
            vectorService.storeCentroid(template.getId(), clusterId);

            templateInfos.add(createTemplateInfo(template));
            log.info("Created template {} for cluster {} with {} messages for trading pair {}",
                    template.getId(), clusterId, clusterMessages.size(), tradingPair);

            templatesCreated++;
        }

        return templatesCreated;
    }

    /**
     * Extract a template from a cluster of similar messages
     */
    private MessageTemplate extractTemplateFromCluster(Integer clusterId, List<SwiftMessageDocument> messages, Map<String, float[]> fieldPatternVectors) {
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("Cannot extract template from empty cluster");
        }

        String messageType = messages.get(0).getMessageType();

        // Log the field pattern for this cluster
        String firstMessagePattern = fieldPatternService.describeFieldPattern(messages.get(0));
        log.info("Cluster {} pattern: {}", clusterId, firstMessagePattern);

        // Extract common structure
        String templateContent = extractCommonStructure(messages);

        // Identify variable fields
        List<MessageTemplate.VariableField> variableFields = identifyVariableFields(messages);

        // Calculate centroid from TEXT embeddings (for semantic matching later)
        // We still need text embeddings stored for matching new messages to templates
        List<float[]> textEmbeddings = new ArrayList<>();
        for (SwiftMessageDocument message : messages) {
            Optional<VectorEmbedding> vectorOpt = vectorService.getVector(message.getId());
            if (vectorOpt.isPresent()) {
                textEmbeddings.add(vectorOpt.get().getEmbedding());
            }
        }

        float[] centroidEmbedding;
        if (!textEmbeddings.isEmpty()) {
            centroidEmbedding = embeddingService.calculateCentroid(textEmbeddings);
        } else {
            // Fallback to empty embedding if no text embeddings available
            log.warn("No text embeddings found for cluster {}, using empty centroid", clusterId);
            centroidEmbedding = new float[384];
        }

        // Calculate confidence based on field pattern similarity (not content)
        double confidence = calculateClusterConfidence(messages, fieldPatternVectors);

        // Calculate template quality score
        double qualityScore = calculateTemplateQuality(messages.size(), confidence, variableFields.size());

        // Get buyer/supplier info from first message
        String buyerId = messages.get(0).getSenderId();
        String sellerId = messages.get(0).getReceiverId();
        String buyerSupplierId = buyerId + " → " + sellerId;

        String priority = messages.size() >= HIGH_VOLUME_THRESHOLD ? "HIGH" : "MEDIUM";

        log.info("Template quality: confidence={}, qualityScore={}, priority={}, volume={}",
                confidence, qualityScore, priority, messages.size());

        return MessageTemplate.builder()
                .messageType(messageType)
                .buyerId(buyerId)
                .sellerId(sellerId)
                .templateContent(templateContent)
                .variableFields(variableFields)
                .clusterId(clusterId)
                .centroidEmbedding(centroidEmbedding)
                .messageCount(messages.size())
                .createdAt(LocalDateTime.now())
                .lastUpdated(LocalDateTime.now())
                .createdBy("system")
                .description(String.format("Template for %s (%s) - %d messages [%s priority, quality: %.2f]",
                        messageType, buyerSupplierId, messages.size(), priority, qualityScore))
                .confidence(confidence)
                .build();
    }

    /**
     * Extract common structure by comparing SWIFT fields across messages
     */
    private String extractCommonStructure(List<SwiftMessageDocument> messages) {
        if (messages.isEmpty()) {
            return "";
        }

        // Parse all messages into field maps
        List<Map<String, String>> parsedMessages = messages.stream()
                .map(this::parseSwiftFields)
                .toList();

        // Get all field tags that appear in messages
        Set<String> allFieldTags = parsedMessages.stream()
                .flatMap(map -> map.keySet().stream())
                .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));

        // Build template by comparing field values across messages
        StringBuilder template = new StringBuilder();

        for (String fieldTag : allFieldTags) {
            // Skip header fields
            if (fieldTag.equals("HEADER")) {
                continue;
            }

            // Collect all values for this field across messages
            List<String> fieldValues = parsedMessages.stream()
                    .filter(map -> map.containsKey(fieldTag))
                    .map(map -> map.get(fieldTag))
                    .toList();

            if (fieldValues.isEmpty()) {
                continue;
            }

            // Check if all values are the same (constant field)
            String firstValue = fieldValues.get(0);
            boolean isConstant = fieldValues.stream().allMatch(firstValue::equals);

            template.append(":").append(fieldTag).append(":");

            if (isConstant) {
                // Field is constant across all messages
                template.append(firstValue);
            } else {
                // Field varies - extract common prefix/pattern
                String commonPattern = extractCommonValuePattern(fieldValues);
                template.append(commonPattern);
            }
            template.append("\n");
        }

        return template.toString().trim();
    }

    /**
     * Parse SWIFT message into field tag -> value map
     */
    private Map<String, String> parseSwiftFields(SwiftMessageDocument message) {
        Map<String, String> fields = new LinkedHashMap<>();
        String content = message.getRawContent();

        Pattern fieldPattern = Pattern.compile(":([0-9]{2}[A-Z]?):(.*?)(?=\n:|$)", Pattern.DOTALL);
        Matcher matcher = fieldPattern.matcher(content);

        while (matcher.find()) {
            String fieldTag = matcher.group(1);
            String fieldValue = matcher.group(2).trim();
            fields.put(fieldTag, fieldValue);
        }

        return fields;
    }

    /**
     * Extract common pattern from varying field values
     */
    private String extractCommonValuePattern(List<String> values) {
        if (values.isEmpty()) {
            return "{VARIABLE}";
        }

        String first = values.get(0);

        // Check if all values have same structure (e.g., all start with "USD")
        String commonPrefix = findCommonPrefix(values);
        String commonSuffix = findCommonSuffix(values);

        if (!commonPrefix.isEmpty() || !commonSuffix.isEmpty()) {
            StringBuilder pattern = new StringBuilder();
            if (!commonPrefix.isEmpty()) {
                pattern.append(commonPrefix);
            }
            pattern.append("{VARIABLE}");
            if (!commonSuffix.isEmpty()) {
                pattern.append(commonSuffix);
            }
            return pattern.toString();
        }

        return "{VARIABLE}";
    }

    /**
     * Find common prefix across all strings
     */
    private String findCommonPrefix(List<String> strings) {
        if (strings.isEmpty()) {
            return "";
        }

        String prefix = strings.get(0);
        for (String s : strings) {
            while (!s.startsWith(prefix) && !prefix.isEmpty()) {
                prefix = prefix.substring(0, prefix.length() - 1);
            }
            if (prefix.isEmpty()) {
                break;
            }
        }

        // Only return prefix if it's meaningful (more than just a letter)
        return prefix.length() > 1 ? prefix : "";
    }

    /**
     * Find common suffix across all strings
     */
    private String findCommonSuffix(List<String> strings) {
        if (strings.isEmpty()) {
            return "";
        }

        String suffix = strings.get(0);
        for (String s : strings) {
            while (!s.endsWith(suffix) && !suffix.isEmpty()) {
                suffix = suffix.substring(1);
            }
            if (suffix.isEmpty()) {
                break;
            }
        }

        // Only return suffix if it's meaningful
        return suffix.length() > 1 ? suffix : "";
    }

    /**
     * Find common pattern between two strings
     */
    private String findCommonPattern(String s1, String s2) {
        String[] lines1 = s1.split("\n");
        String[] lines2 = s2.split("\n");

        StringBuilder common = new StringBuilder();

        int maxLines = Math.min(lines1.length, lines2.length);
        for (int i = 0; i < maxLines; i++) {
            String commonLine = findCommonInLine(lines1[i], lines2[i]);
            if (!commonLine.isEmpty()) {
                common.append(commonLine).append("\n");
            }
        }

        return common.toString();
    }

    /**
     * Find common parts in a single line
     */
    private String findCommonInLine(String line1, String line2) {
        String[] tokens1 = line1.split(":");
        String[] tokens2 = line2.split(":");

        if (tokens1.length != tokens2.length) {
            return tokens1.length > 0 ? tokens1[0] : "";
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < tokens1.length; i++) {
            if (i < tokens2.length && tokens1[i].trim().equals(tokens2[i].trim())) {
                result.append(tokens1[i]);
                if (i < tokens1.length - 1) {
                    result.append(":");
                }
            } else {
                result.append("{{VAR}}");
                if (i < tokens1.length - 1) {
                    result.append(":");
                }
            }
        }

        return result.toString();
    }

    /**
     * Replace variable patterns with placeholders
     */
    private String replaceVariablesWithPlaceholders(String content) {
        // Replace amounts
        content = AMOUNT_PATTERN.matcher(content).replaceAll("{{AMOUNT}}");

        // Replace dates
        content = DATE_PATTERN.matcher(content).replaceAll("{{DATE}}");

        // Replace long alphanumeric codes
        content = ALPHANUMERIC_PATTERN.matcher(content).replaceAll("{{CODE}}");

        // Replace other numbers
        content = NUMERIC_PATTERN.matcher(content).replaceAll("{{NUMBER}}");

        return content;
    }

    /**
     * Identify variable fields across messages
     */
    private List<MessageTemplate.VariableField> identifyVariableFields(List<SwiftMessageDocument> messages) {
        List<MessageTemplate.VariableField> fields = new ArrayList<>();

        // Extract SWIFT field tags (e.g., :20:, :32A:, :50K:)
        Pattern fieldPattern = Pattern.compile(":([0-9]{2}[A-Z]?):");

        Map<String, Set<String>> fieldValues = new HashMap<>();

        for (SwiftMessageDocument message : messages) {
            Matcher matcher = fieldPattern.matcher(message.getRawContent());
            while (matcher.find()) {
                String fieldTag = matcher.group(1);
                fieldValues.computeIfAbsent(fieldTag, k -> new HashSet<>());

                // Extract value after field tag
                int start = matcher.end();
                int end = message.getRawContent().indexOf("\n:", start);
                if (end == -1) {
                    end = message.getRawContent().length();
                }
                String value = message.getRawContent().substring(start, end).trim();
                fieldValues.get(fieldTag).add(value);
            }
        }

        // Create variable field objects
        for (Map.Entry<String, Set<String>> entry : fieldValues.entrySet()) {
            String tag = entry.getKey();
            Set<String> values = entry.getValue();

            MessageTemplate.VariableField.FieldType type = determineFieldType(values);

            fields.add(MessageTemplate.VariableField.builder()
                    .fieldTag(tag)
                    .fieldName("Field_" + tag)
                    .type(type)
                    .sampleValues(new ArrayList<>(values).subList(0, Math.min(5, values.size())))
                    .required(true)
                    .build());
        }

        return fields;
    }

    /**
     * Determine field type based on values
     */
    private MessageTemplate.VariableField.FieldType determineFieldType(Set<String> values) {
        if (values.isEmpty()) {
            return MessageTemplate.VariableField.FieldType.TEXT;
        }

        boolean allNumeric = values.stream().allMatch(v -> v.matches("\\d+"));
        boolean allDates = values.stream().allMatch(v -> DATE_PATTERN.matcher(v).find());
        boolean allAmounts = values.stream().allMatch(v -> AMOUNT_PATTERN.matcher(v).find());

        if (allAmounts) {
            return MessageTemplate.VariableField.FieldType.AMOUNT;
        } else if (allDates) {
            return MessageTemplate.VariableField.FieldType.DATE;
        } else if (allNumeric) {
            return MessageTemplate.VariableField.FieldType.NUMERIC;
        } else if (values.stream().allMatch(v -> v.matches("[A-Z0-9]+"))) {
            return MessageTemplate.VariableField.FieldType.CODE;
        } else if (values.stream().allMatch(v -> v.matches("[A-Za-z0-9]+"))) {
            return MessageTemplate.VariableField.FieldType.ALPHANUMERIC;
        } else {
            return MessageTemplate.VariableField.FieldType.TEXT;
        }
    }

    /**
     * Calculate cluster confidence based on message similarity
     */
    private double calculateClusterConfidence(List<SwiftMessageDocument> messages, Map<String, float[]> embeddingMap) {
        if (messages.size() < 2) {
            return 1.0;
        }

        List<Double> similarities = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            for (int j = i + 1; j < messages.size(); j++) {
                String id1 = messages.get(i).getId();
                String id2 = messages.get(j).getId();

                if (embeddingMap.containsKey(id1) && embeddingMap.containsKey(id2)) {
                    double sim = embeddingService.cosineSimilarity(
                            embeddingMap.get(id1),
                            embeddingMap.get(id2)
                    );
                    similarities.add(sim);
                }
            }
        }

        return similarities.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    /**
     * Create response object
     */
    private Map<String, Object> createResponse(int totalMessages, int clustersCreated,
                                               List<Map<String, Object>> templates) {
        Map<String, Object> response = new HashMap<>();
        response.put("totalMessages", totalMessages);
        response.put("clustersCreated", clustersCreated);
        response.put("templates", templates);
        response.put("status", "success");
        response.put("message", String.format("Successfully extracted %d templates from %d messages",
                clustersCreated, totalMessages));
        return response;
    }

    /**
     * Create template info map
     */
    private Map<String, Object> createTemplateInfo(MessageTemplate template) {
        Map<String, Object> info = new HashMap<>();
        info.put("templateId", template.getId());
        info.put("messageType", template.getMessageType());
        info.put("messageCount", template.getMessageCount());
        info.put("confidence", template.getConfidence());
        info.put("description", template.getDescription());
        return info;
    }

    /**
     * Select the top N clusters from multiple clusters for a trading pair
     * Ranked by: size (60%) + cohesion (40%) - filters out noise while keeping multiple patterns
     * Returns empty list if no suitable clusters found
     */
    private List<Map.Entry<Integer, List<String>>> selectTopClusters(
            Map<Integer, List<String>> clusters,
            List<SwiftMessageDocument> allMessages,
            Map<String, float[]> combinedVectors,
            int maxClusters) {

        if (clusters.isEmpty()) {
            return new ArrayList<>();
        }

        // Calculate scores for all clusters
        List<ClusterScore> scoredClusters = new ArrayList<>();

        for (Map.Entry<Integer, List<String>> cluster : clusters.entrySet()) {
            List<String> messageIds = cluster.getValue();

            // Skip clusters that are too small
            if (messageIds.size() < MIN_CLUSTER_SIZE) {
                log.debug("Skipping cluster {} - only {} messages (minimum: {})",
                        cluster.getKey(), messageIds.size(), MIN_CLUSTER_SIZE);
                continue;
            }

            // Calculate cluster cohesion (average pairwise similarity)
            double cohesion = calculateClusterCohesion(messageIds, combinedVectors);

            // Score = weighted combination of size and cohesion
            // Larger clusters are preferred, but cohesion matters too
            double sizeScore = (double) messageIds.size() / allMessages.size();
            double score = (0.6 * sizeScore) + (0.4 * cohesion);

            scoredClusters.add(new ClusterScore(cluster, score, messageIds.size(), cohesion));

            log.debug("Cluster {} - size: {}, cohesion: {:.3f}, score: {:.3f}",
                    cluster.getKey(), messageIds.size(), cohesion, score);
        }

        // Sort by score descending and take top N
        scoredClusters.sort((a, b) -> Double.compare(b.score, a.score));

        List<Map.Entry<Integer, List<String>>> topClusters = scoredClusters.stream()
                .limit(maxClusters)
                .map(cs -> cs.cluster)
                .collect(Collectors.toList());

        if (!topClusters.isEmpty()) {
            log.info("Selected top {} clusters from {} total (sizes: {})",
                    topClusters.size(),
                    clusters.size(),
                    scoredClusters.stream()
                            .limit(maxClusters)
                            .map(cs -> cs.size)
                            .collect(Collectors.toList()));
        }

        return topClusters;
    }

    /**
     * Helper class to store cluster with its score
     */
    private static class ClusterScore {
        final Map.Entry<Integer, List<String>> cluster;
        final double score;
        final int size;
        final double cohesion;

        ClusterScore(Map.Entry<Integer, List<String>> cluster, double score, int size, double cohesion) {
            this.cluster = cluster;
            this.score = score;
            this.size = size;
            this.cohesion = cohesion;
        }
    }

    /**
     * Calculate cluster cohesion (average pairwise similarity within cluster)
     */
    private double calculateClusterCohesion(List<String> messageIds, Map<String, float[]> vectors) {
        if (messageIds.size() < 2) {
            return 1.0;  // Perfect cohesion for single message
        }

        double totalSimilarity = 0.0;
        int pairCount = 0;

        for (int i = 0; i < messageIds.size(); i++) {
            for (int j = i + 1; j < messageIds.size(); j++) {
                String id1 = messageIds.get(i);
                String id2 = messageIds.get(j);

                float[] vec1 = vectors.get(id1);
                float[] vec2 = vectors.get(id2);

                if (vec1 != null && vec2 != null) {
                    double similarity = embeddingService.cosineSimilarity(vec1, vec2);
                    totalSimilarity += similarity;
                    pairCount++;
                }
            }
        }

        return pairCount > 0 ? totalSimilarity / pairCount : 0.0;
    }

    /**
     * Filter out outliers using percentile-based and standard deviation methods
     */
    private List<SwiftMessageDocument> filterOutliers(List<SwiftMessageDocument> messages,
                                                      Map<String, float[]> fieldPatternVectors) {
        if (messages.size() <= MIN_CLUSTER_SIZE) {
            return messages;  // Don't filter if cluster is at minimum size
        }

        // Calculate pairwise similarities
        List<MessageSimilarity> similarities = calculatePairwiseSimilarities(messages, fieldPatternVectors);

        if (similarities.isEmpty()) {
            return messages;
        }

        // Method 1: Percentile-based filtering (Interquartile Range)
        List<SwiftMessageDocument> iqrFiltered = filterByIQR(messages, similarities);

        // Method 2: Standard deviation-based filtering
        List<SwiftMessageDocument> stdDevFiltered = filterByStdDev(messages, fieldPatternVectors);

        // Use intersection of both methods for robust filtering
        List<SwiftMessageDocument> filtered = new ArrayList<>(iqrFiltered);
        filtered.retainAll(stdDevFiltered);

        // Ensure we keep at least MIN_CLUSTER_SIZE messages
        if (filtered.size() < MIN_CLUSTER_SIZE) {
            log.warn("Filtering would reduce cluster below minimum size, using less aggressive filtering");
            return iqrFiltered.size() >= MIN_CLUSTER_SIZE ? iqrFiltered : messages;
        }

        return filtered;
    }

    /**
     * Calculate pairwise similarities between messages
     */
    private List<MessageSimilarity> calculatePairwiseSimilarities(List<SwiftMessageDocument> messages,
                                                                  Map<String, float[]> fieldPatternVectors) {
        List<MessageSimilarity> similarities = new ArrayList<>();

        for (int i = 0; i < messages.size(); i++) {
            double totalSimilarity = 0.0;
            int comparisonCount = 0;

            for (int j = 0; j < messages.size(); j++) {
                if (i != j) {
                    String id1 = messages.get(i).getId();
                    String id2 = messages.get(j).getId();

                    if (fieldPatternVectors.containsKey(id1) && fieldPatternVectors.containsKey(id2)) {
                        double sim = embeddingService.cosineSimilarity(
                                fieldPatternVectors.get(id1),
                                fieldPatternVectors.get(id2)
                        );
                        totalSimilarity += sim;
                        comparisonCount++;
                    }
                }
            }

            double avgSimilarity = comparisonCount > 0 ? totalSimilarity / comparisonCount : 0.0;
            similarities.add(new MessageSimilarity(messages.get(i), avgSimilarity));
        }

        return similarities;
    }

    /**
     * Filter messages using Interquartile Range (IQR) method
     * Keeps messages with similarity scores between 25th and 75th percentile
     */
    private List<SwiftMessageDocument> filterByIQR(List<SwiftMessageDocument> messages,
                                                   List<MessageSimilarity> similarities) {
        List<Double> scores = similarities.stream()
                .map(MessageSimilarity::getSimilarity)
                .sorted()
                .collect(Collectors.toList());

        if (scores.isEmpty()) {
            return messages;
        }

        // Calculate percentiles
        double q25 = calculatePercentile(scores, 0.25);
        double q75 = calculatePercentile(scores, 0.75);
        double iqr = q75 - q25;

        // Use 1.5 * IQR for outlier detection (standard method)
        double lowerBound = q25 - 1.5 * iqr;
        double upperBound = q75 + 1.5 * iqr;

        log.debug("IQR filtering: Q25={}, Q75={}, IQR={}, bounds=[{}, {}]",
                q25, q75, iqr, lowerBound, upperBound);

        return similarities.stream()
                .filter(ms -> ms.getSimilarity() >= lowerBound && ms.getSimilarity() <= upperBound)
                .map(MessageSimilarity::getMessage)
                .collect(Collectors.toList());
    }

    /**
     * Filter messages using standard deviation method
     * Removes messages more than N standard deviations from mean
     */
    private List<SwiftMessageDocument> filterByStdDev(List<SwiftMessageDocument> messages,
                                                      Map<String, float[]> fieldPatternVectors) {
        if (messages.isEmpty()) {
            return messages;
        }

        // Calculate centroid
        List<float[]> vectors = messages.stream()
                .map(m -> fieldPatternVectors.get(m.getId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (vectors.isEmpty()) {
            return messages;
        }

        float[] centroid = calculateFieldPatternCentroid(vectors);

        // Calculate similarities to centroid
        List<Double> similarities = new ArrayList<>();
        for (SwiftMessageDocument message : messages) {
            float[] vector = fieldPatternVectors.get(message.getId());
            if (vector != null) {
                double sim = embeddingService.cosineSimilarity(vector, centroid);
                similarities.add(sim);
            } else {
                similarities.add(0.0);
            }
        }

        // Calculate mean and standard deviation
        double mean = similarities.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = similarities.stream()
                .mapToDouble(s -> Math.pow(s - mean, 2))
                .average()
                .orElse(0.0);
        double stdDev = Math.sqrt(variance);

        log.debug("StdDev filtering: mean={}, stdDev={}, threshold={}",
                mean, stdDev, OUTLIER_STD_DEV_THRESHOLD);

        // Filter messages within threshold
        List<SwiftMessageDocument> filtered = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            double sim = similarities.get(i);
            if (Math.abs(sim - mean) <= OUTLIER_STD_DEV_THRESHOLD * stdDev) {
                filtered.add(messages.get(i));
            } else {
                log.debug("Removing outlier message {} with similarity {} (mean={}, threshold={})",
                        messages.get(i).getId(), sim, mean, OUTLIER_STD_DEV_THRESHOLD * stdDev);
            }
        }

        return filtered;
    }

    /**
     * Calculate percentile value from sorted list
     */
    private double calculatePercentile(List<Double> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) {
            return 0.0;
        }

        int index = (int) Math.ceil(percentile * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        return sortedValues.get(index);
    }

    /**
     * Calculate template quality score based on volume and consistency
     */
    private double calculateTemplateQuality(int clusterSize, double avgSimilarity, int fieldCount) {
        // Volume score: logarithmic scale (0.0 - 1.0)
        double volumeScore = Math.min(1.0, Math.log10(clusterSize + 1) / Math.log10(HIGH_VOLUME_THRESHOLD + 1));

        // Similarity score: already 0.0 - 1.0
        double similarityScore = avgSimilarity;

        // Field consistency score: more fields = more structure = better
        double fieldScore = Math.min(1.0, fieldCount / 10.0);

        // Weighted average: volume is most important for noise resistance
        return 0.5 * volumeScore + 0.3 * similarityScore + 0.2 * fieldScore;
    }

    /**
     * Combine field pattern vectors and text embeddings for hybrid clustering
     * This allows semantic similarity to work alongside structural patterns
     */
    private Map<String, float[]> combineFeaturesForClustering(
            Map<String, float[]> fieldPatterns,
            Map<String, float[]> textEmbeddings) {

        Map<String, float[]> combinedVectors = new HashMap<>();

        // Only include messages that have both field patterns and text embeddings
        for (String messageId : fieldPatterns.keySet()) {
            if (!textEmbeddings.containsKey(messageId)) {
                log.warn("Message {} has field pattern but no text embedding, skipping", messageId);
                continue;
            }

            float[] fieldPattern = fieldPatterns.get(messageId);
            float[] textEmbedding = textEmbeddings.get(messageId);

            // Concatenate field pattern and text embedding into combined vector
            // Format: [field_pattern_features... | text_embedding_features...]
            float[] combined = new float[fieldPattern.length + textEmbedding.length];

            System.arraycopy(fieldPattern, 0, combined, 0, fieldPattern.length);
            System.arraycopy(textEmbedding, 0, combined, fieldPattern.length, textEmbedding.length);

            combinedVectors.put(messageId, combined);
        }

        log.info("Combined {} field pattern vectors (dim={}) with text embeddings (dim={}) into hybrid vectors (dim={})",
                combinedVectors.size(),
                fieldPatterns.values().iterator().next().length,
                textEmbeddings.values().iterator().next().length,
                combinedVectors.values().iterator().next().length);

        return combinedVectors;
    }

    /**
     * Calculate centroid for field pattern vectors (variable dimension)
     */
    private float[] calculateFieldPatternCentroid(List<float[]> vectors) {
        if (vectors.isEmpty()) {
            return new float[0];
        }

        // Determine dimension from first vector
        int dimension = vectors.get(0).length;
        float[] centroid = new float[dimension];

        // Sum all vectors
        for (float[] vector : vectors) {
            if (vector.length != dimension) {
                log.warn("Inconsistent vector dimensions: expected {}, got {}", dimension, vector.length);
                continue;
            }
            for (int i = 0; i < dimension; i++) {
                centroid[i] += vector[i];
            }
        }

        // Average
        for (int i = 0; i < dimension; i++) {
            centroid[i] /= vectors.size();
        }

        return centroid;
    }

    /**
     * Helper class to store message with its similarity score
     */
    private static class MessageSimilarity {
        private final SwiftMessageDocument message;
        private final double similarity;

        public MessageSimilarity(SwiftMessageDocument message, double similarity) {
            this.message = message;
            this.similarity = similarity;
        }

        public SwiftMessageDocument getMessage() {
            return message;
        }

        public double getSimilarity() {
            return similarity;
        }
    }

    /**
     * Test a message content against all templates without saving
     * Returns similarity scores for playground/testing
     */
    public Map<String, Object> testMessageAgainstTemplates(String rawContent, String messageType) {
        log.info("Testing message against templates for type: {}", messageType);

        // Get all templates for this message type
        List<MessageTemplate> templates = templateRepository.findByMessageType(messageType);

        if (templates.isEmpty()) {
            return Map.of(
                "matches", Collections.emptyList(),
                "message", "No templates found for message type: " + messageType
            );
        }

        // Generate embedding for the test message
        float[] testEmbedding = embeddingService.generateEmbedding(rawContent);

        // Calculate similarity with each template
        List<Map<String, Object>> matches = new ArrayList<>();

        for (MessageTemplate template : templates) {
            // Calculate text-based similarity (using Sentence-BERT embeddings)
            double textSimilarity = embeddingService.cosineSimilarity(
                testEmbedding,
                template.getCentroidEmbedding()
            );

            // Extract field patterns for structural similarity
            Map<String, String> testFields = fieldPatternService.extractFieldsWithContent(rawContent);

            // Calculate field-level similarity
            Map<String, Map<String, Object>> fieldSimilarities = calculateFieldLevelSimilarity(testFields, template);

            // Calculate average field-level similarity
            double fieldLevelSimilarity = calculateAverageFieldSimilarity(fieldSimilarities);

            // Simple structural similarity: check if key fields match
            double structuralSimilarity = calculateStructuralSimilarity(testFields, template);

            // Combined similarity (weighted average)
            // Field-level similarity is most important (50%), then structural (30%), then document-level text (20%)
            double combinedSimilarity = (fieldLevelSimilarity * 0.5) + (structuralSimilarity * 0.3) + (textSimilarity * 0.2);

            Map<String, Object> matchData = new HashMap<>();
            matchData.put("templateId", template.getId());
            matchData.put("messageType", template.getMessageType());
            matchData.put("description", template.getDescription());
            matchData.put("messageCount", template.getMessageCount());
            matchData.put("confidence", template.getConfidence());
            matchData.put("textSimilarity", textSimilarity);
            matchData.put("structuralSimilarity", structuralSimilarity);
            matchData.put("fieldLevelSimilarity", fieldLevelSimilarity);
            matchData.put("combinedSimilarity", combinedSimilarity);
            matchData.put("fieldSimilarities", fieldSimilarities);

            matches.add(matchData);
        }

        // Sort by combined similarity (highest first)
        matches.sort((a, b) -> Double.compare(
            (Double) b.get("combinedSimilarity"),
            (Double) a.get("combinedSimilarity")
        ));

        // Get the best match
        Map<String, Object> bestMatch = matches.isEmpty() ? null : matches.get(0);

        return Map.of(
            "matches", matches,
            "bestMatch", bestMatch,
            "totalTemplates", templates.size(),
            "message", "Found " + matches.size() + " template matches"
        );
    }

    /**
     * Calculate field-level similarity with pattern matching
     * Returns a map of field tag -> {similarity, matchesPattern, reason}
     */
    private Map<String, Map<String, Object>> calculateFieldLevelSimilarity(
            Map<String, String> testFields, MessageTemplate template) {

        Map<String, Map<String, Object>> fieldSimilarities = new HashMap<>();

        // Parse template content to extract field patterns
        Map<String, String> templateFieldPatterns = extractTemplateFieldPatterns(template.getTemplateContent());

        // Calculate similarity for each test field
        for (Map.Entry<String, String> testField : testFields.entrySet()) {
            String fieldTag = testField.getKey();
            String testValue = testField.getValue();

            Map<String, Object> fieldScore = new HashMap<>();

            if (templateFieldPatterns.containsKey(fieldTag)) {
                String templatePattern = templateFieldPatterns.get(fieldTag);

                // Check if test value matches the pattern
                if (matchesPattern(testValue, templatePattern)) {
                    // Perfect match - follows the template pattern
                    fieldScore.put("similarity", 1.0);
                    fieldScore.put("matchesPattern", true);
                    fieldScore.put("reason", "Matches template pattern");
                } else {
                    // Doesn't match pattern - calculate semantic similarity
                    double semanticSimilarity = calculateSemanticSimilarity(testValue, templatePattern);
                    fieldScore.put("similarity", semanticSimilarity);
                    fieldScore.put("matchesPattern", false);
                    fieldScore.put("reason", "Different structure - using semantic similarity");
                }
            } else {
                // Field not in template
                fieldScore.put("similarity", 0.0);
                fieldScore.put("matchesPattern", false);
                fieldScore.put("reason", "Field not in template");
            }

            fieldSimilarities.put(fieldTag, fieldScore);
        }

        return fieldSimilarities;
    }

    /**
     * Calculate average field-level similarity from field similarity map
     */
    private double calculateAverageFieldSimilarity(Map<String, Map<String, Object>> fieldSimilarities) {
        if (fieldSimilarities == null || fieldSimilarities.isEmpty()) {
            return 0.0;
        }

        double totalSimilarity = 0.0;
        int count = 0;

        for (Map<String, Object> fieldScore : fieldSimilarities.values()) {
            Object simObj = fieldScore.get("similarity");
            if (simObj instanceof Number) {
                totalSimilarity += ((Number) simObj).doubleValue();
                count++;
            }
        }

        return count > 0 ? totalSimilarity / count : 0.0;
    }

    /**
     * Extract field patterns from template content
     * Returns map of field tag -> pattern content
     */
    private Map<String, String> extractTemplateFieldPatterns(String templateContent) {
        Map<String, String> patterns = new HashMap<>();
        if (templateContent == null) return patterns;

        String[] lines = templateContent.split("\n");
        String currentTag = null;
        StringBuilder currentContent = new StringBuilder();

        for (String line : lines) {
            // Match :TAG: or {TAG} patterns
            java.util.regex.Pattern tagPattern = java.util.regex.Pattern.compile("^:([0-9A-Z]+):|^\\{([0-9A-Z]+)\\}");
            java.util.regex.Matcher matcher = tagPattern.matcher(line.trim());

            if (matcher.find()) {
                // Save previous field
                if (currentTag != null) {
                    patterns.put(currentTag, currentContent.toString().trim());
                }

                // Start new field
                currentTag = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                currentContent = new StringBuilder(line);
            } else if (currentTag != null) {
                // Continue current field
                currentContent.append("\n").append(line);
            }
        }

        // Save last field
        if (currentTag != null) {
            patterns.put(currentTag, currentContent.toString().trim());
        }

        return patterns;
    }

    /**
     * Check if a test value matches a template pattern (with variables)
     */
    private boolean matchesPattern(String testValue, String pattern) {
        // Remove the field tag prefix from both
        String cleanTest = testValue.replaceFirst("^:[0-9A-Z]+:", "").trim();
        String cleanPattern = pattern.replaceFirst("^:[0-9A-Z]+:", "").replaceFirst("^\\{[0-9A-Z]+\\}", "").trim();

        // Convert pattern variables {varName} to regex wildcards
        String regexPattern = cleanPattern
            .replaceAll("\\{[^}]+\\}", ".*?")  // Replace {var} with .*?
            .replaceAll("\\$\\{[^}]+\\}", ".*?"); // Replace ${var} with .*?

        // Escape special regex characters except our wildcards
        regexPattern = "^" + regexPattern + "$";

        try {
            return cleanTest.matches(regexPattern);
        } catch (Exception e) {
            log.warn("Pattern matching failed for pattern: {}", regexPattern, e);
            return false;
        }
    }

    /**
     * Calculate semantic similarity between two field values using Sentence-BERT
     */
    private double calculateSemanticSimilarity(String testValue, String templatePattern) {
        try {
            // Remove field tags
            String cleanTest = testValue.replaceFirst("^:[0-9A-Z]+:", "").trim();
            String cleanPattern = templatePattern
                .replaceFirst("^:[0-9A-Z]+:", "")
                .replaceFirst("^\\{[0-9A-Z]+\\}", "")
                .replaceAll("\\{[^}]+\\}", "")  // Remove variable placeholders
                .replaceAll("\\$\\{[^}]+\\}", "")
                .trim();

            // Generate embeddings
            float[] testEmbedding = embeddingService.generateEmbedding(cleanTest);
            float[] patternEmbedding = embeddingService.generateEmbedding(cleanPattern);

            // Calculate cosine similarity
            return embeddingService.cosineSimilarity(testEmbedding, patternEmbedding);
        } catch (Exception e) {
            log.warn("Semantic similarity calculation failed", e);
            return 0.5; // Default to medium similarity on error
        }
    }

    /**
     * Calculate structural similarity based on field patterns
     */
    private double calculateStructuralSimilarity(Map<String, String> testFields, MessageTemplate template) {
        // Get template's variable fields
        List<MessageTemplate.VariableField> variableFields = template.getVariableFields();
        if (variableFields == null || variableFields.isEmpty()) {
            return 1.0; // No variable fields to compare
        }

        // Extract field tags from template
        Set<String> templateFieldTags = variableFields.stream()
            .map(MessageTemplate.VariableField::getFieldTag)
            .collect(Collectors.toSet());

        // Check overlap between test fields and template fields
        Set<String> testFieldTags = testFields.keySet();

        // Calculate Jaccard similarity
        Set<String> intersection = new HashSet<>(testFieldTags);
        intersection.retainAll(templateFieldTags);

        Set<String> union = new HashSet<>(testFieldTags);
        union.addAll(templateFieldTags);

        if (union.isEmpty()) {
            return 1.0;
        }

        return (double) intersection.size() / union.size();
    }
}
