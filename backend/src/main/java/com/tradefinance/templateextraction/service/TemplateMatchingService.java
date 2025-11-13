package com.tradefinance.templateextraction.service;

import com.tradefinance.templateextraction.dto.TemplateMatchResponse;
import com.tradefinance.templateextraction.model.MessageTemplate;
import com.tradefinance.templateextraction.model.Transaction;
import com.tradefinance.templateextraction.model.VectorEmbedding;
import com.tradefinance.templateextraction.model.mongo.SwiftMessageDocument;
import com.tradefinance.templateextraction.repository.MessageTemplateRepository;
import com.tradefinance.templateextraction.repository.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class TemplateMatchingService {

    @Value("${template.extraction.similarity.threshold}")
    private double similarityThreshold;

    private final MessageTemplateRepository templateRepository;
    private final TransactionRepository transactionRepository;
    private final EmbeddingService embeddingService;
    private final VectorService vectorService;
    private final com.tradefinance.templateextraction.repository.mongo.SwiftMessageMongoRepository messageRepository;
    private final LLMComparisonService llmComparisonService;

    public TemplateMatchingService(MessageTemplateRepository templateRepository,
                                  TransactionRepository transactionRepository,
                                  EmbeddingService embeddingService,
                                  VectorService vectorService,
                                  com.tradefinance.templateextraction.repository.mongo.SwiftMessageMongoRepository messageRepository,
                                  LLMComparisonService llmComparisonService) {
        this.templateRepository = templateRepository;
        this.transactionRepository = transactionRepository;
        this.embeddingService = embeddingService;
        this.vectorService = vectorService;
        this.messageRepository = messageRepository;
        this.llmComparisonService = llmComparisonService;
    }

    /**
     * Preview match - calculate field confidences without saving
     * This is used for template validation view to show confidence scores on-the-fly
     */
    public Map<String, Double> previewMatchFieldConfidences(String messageId, String templateId) {
        log.info("Preview matching message {} against template {}", messageId, templateId);

        // Get message
        SwiftMessageDocument message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));

        // Get template
        MessageTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));

        // Extract fields from message
        Map<String, Object> extractedFields = extractFieldsFromMessage(message, template);

        // Calculate and return field confidences without saving
        return calculateFieldConfidences(message, template, extractedFields);
    }

    /**
     * Match an incoming message to the best template
     */
    public TemplateMatchResponse matchMessageToTemplate(SwiftMessageDocument message) {
        log.info("Matching message {} to templates", message.getId());

        // Get message embedding from VectorService
        Optional<VectorEmbedding> vectorOpt = vectorService.getVector(message.getId());
        if (vectorOpt.isEmpty()) {
            log.error("No embedding found for message {}", message.getId());
            return TemplateMatchResponse.builder()
                    .messageType(message.getMessageType())
                    .matchConfidence(0.0)
                    .requiresManualReview(true)
                    .message("No embedding found for message")
                    .build();
        }

        float[] messageEmbedding = vectorOpt.get().getEmbedding();

        // Get templates for specific trading pair (buyer-seller combination)
        List<MessageTemplate> templates = templateRepository
                .findByMessageTypeAndBuyerIdAndSellerIdOrderByConfidenceDesc(
                        message.getMessageType(),
                        message.getSenderId(),  // buyerId
                        message.getReceiverId()  // sellerId
                );

        if (templates.isEmpty()) {
            log.warn("No templates found for trading pair {} -> {} (type: {})",
                    message.getSenderId(), message.getReceiverId(), message.getMessageType());
            return TemplateMatchResponse.builder()
                    .messageType(message.getMessageType())
                    .matchConfidence(0.0)
                    .requiresManualReview(true)
                    .message(String.format("No templates found for trading pair %s -> %s (type: %s)",
                            message.getSenderId(), message.getReceiverId(), message.getMessageType()))
                    .build();
        }

        // Find best matching template
        MessageTemplate bestTemplate = null;
        double bestSimilarity = 0.0;

        for (MessageTemplate template : templates) {
            double similarity = embeddingService.cosineSimilarity(
                    messageEmbedding,
                    template.getCentroidEmbedding()
            );

            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestTemplate = template;
            }
        }

        if (bestTemplate == null || bestSimilarity < similarityThreshold) {
            return TemplateMatchResponse.builder()
                    .messageType(message.getMessageType())
                    .matchConfidence(bestSimilarity)
                    .requiresManualReview(true)
                    .message("No suitable template found (similarity: " + bestSimilarity + ")")
                    .build();
        }

        // Extract data based on template
        Map<String, Object> extractedFields = extractFieldsFromMessage(message, bestTemplate);
        Map<String, Object> suggestedValues = generateSuggestedValues(bestTemplate, extractedFields);

        // Calculate field-level confidences
        Map<String, Double> fieldConfidences = calculateFieldConfidences(message, bestTemplate, extractedFields);

        // Generate LLM comparison (text format for backwards compatibility)
        String llmComparison = llmComparisonService.compareTransactionToTemplate(
                message.getRawContent(),
                bestTemplate.getTemplateContent(),
                extractedFields
        );

        // Generate structured analysis with field-level findings
        com.tradefinance.templateextraction.dto.StructuredAnalysis structuredAnalysis =
                llmComparisonService.getStructuredAnalysis(
                        message.getRawContent(),
                        bestTemplate.getTemplateContent(),
                        extractedFields
                );

        // Create transaction
        Transaction transaction = createTransaction(message, bestTemplate, extractedFields, bestSimilarity, fieldConfidences);
        transaction.setLlmComparison(llmComparison);
        transaction.setStructuredAnalysis(structuredAnalysis);
        transactionRepository.save(transaction);

        // Update message
        message.setTemplateId(bestTemplate.getId());
        message.setStatus(SwiftMessageDocument.ProcessingStatus.TEMPLATE_MATCHED);

        return TemplateMatchResponse.builder()
                .templateId(bestTemplate.getId())
                .messageType(message.getMessageType())
                .matchConfidence(bestSimilarity)
                .extractedFields(extractedFields)
                .suggestedValues(suggestedValues)
                .llmComparison(llmComparison)
                .requiresManualReview(bestSimilarity < 0.95)
                .message("Successfully matched to template " + bestTemplate.getId())
                .build();
    }

    /**
     * Extract fields from message based on template
     */
    private Map<String, Object> extractFieldsFromMessage(SwiftMessageDocument message, MessageTemplate template) {
        Map<String, Object> fields = new HashMap<>();

        // Extract SWIFT field tags
        Pattern fieldPattern = Pattern.compile(":([0-9]{2}[A-Z]?):(.*?)(?=\\n:|$)", Pattern.DOTALL);
        Matcher matcher = fieldPattern.matcher(message.getRawContent());

        while (matcher.find()) {
            String fieldTag = matcher.group(1);
            String value = matcher.group(2).trim();
            fields.put(fieldTag, value);
        }

        // Add basic message info
        fields.put("senderId", message.getSenderId());
        fields.put("receiverId", message.getReceiverId());
        fields.put("timestamp", message.getTimestamp().toString());

        return fields;
    }

    /**
     * Generate suggested values based on template variable fields
     */
    private Map<String, Object> generateSuggestedValues(MessageTemplate template,
                                                        Map<String, Object> extractedFields) {
        Map<String, Object> suggestions = new HashMap<>();

        for (MessageTemplate.VariableField varField : template.getVariableFields()) {
            String fieldTag = varField.getFieldTag();

            if (extractedFields.containsKey(fieldTag)) {
                Object value = extractedFields.get(fieldTag);

                // Add type-specific suggestions
                switch (varField.getType()) {
                    case AMOUNT:
                        suggestions.put(fieldTag + "_formatted", formatAmount(value.toString()));
                        break;
                    case DATE:
                        suggestions.put(fieldTag + "_formatted", formatDate(value.toString()));
                        break;
                    case CODE:
                        suggestions.put(fieldTag + "_validated", validateCode(value.toString()));
                        break;
                    default:
                        suggestions.put(fieldTag, value);
                }
            } else if (!varField.getSampleValues().isEmpty()) {
                // Suggest from sample values if field is missing
                suggestions.put(fieldTag + "_suggested", varField.getSampleValues().get(0));
            }
        }

        return suggestions;
    }

    /**
     * Calculate field-level confidence scores using semantic similarity
     */
    private Map<String, Double> calculateFieldConfidences(SwiftMessageDocument message,
                                                          MessageTemplate template,
                                                          Map<String, Object> extractedFields) {
        Map<String, Double> confidences = new HashMap<>();

        // Parse template content to extract field values
        Map<String, String> templateFields = parseSwiftFields(template.getTemplateContent());

        // Get template's variable fields
        Set<String> variableFieldTags = template.getVariableFields().stream()
                .map(MessageTemplate.VariableField::getFieldTag)
                .collect(java.util.stream.Collectors.toSet());

        // Calculate confidence for each extracted field
        for (String fieldTag : extractedFields.keySet()) {
            // Skip non-SWIFT field tags (like senderId, timestamp, etc.)
            if (!fieldTag.matches("\\d{2}[A-Z]?")) {
                continue;
            }

            double confidence;

            // Get field values
            String messageFieldValue = extractedFields.get(fieldTag).toString();
            String templateFieldValue = templateFields.get(fieldTag);

            if (templateFieldValue != null && !templateFieldValue.isEmpty()) {
                // Calculate semantic similarity between template and message field
                confidence = embeddingService.calculateFieldSimilarity(
                        templateFieldValue,
                        messageFieldValue
                );

                // Ensure minimum confidence for matched fields
                confidence = Math.max(confidence, 0.5);
            } else if (variableFieldTags.contains(fieldTag)) {
                // Variable field with no template value - assign high confidence
                confidence = 0.95;
            } else {
                // Fixed field - assign perfect confidence
                confidence = 1.0;
            }

            confidences.put(fieldTag, confidence);
        }

        return confidences;
    }

    /**
     * Parse SWIFT message/template fields into a map
     */
    private Map<String, String> parseSwiftFields(String content) {
        Map<String, String> fields = new HashMap<>();

        // Extract SWIFT field tags (format :TAG:VALUE)
        Pattern fieldPattern = Pattern.compile(":([0-9]{2}[A-Z]?):(.*?)(?=\\n:|$)", Pattern.DOTALL);
        Matcher matcher = fieldPattern.matcher(content);

        while (matcher.find()) {
            String tag = matcher.group(1);
            String value = matcher.group(2).trim();
            fields.put(tag, value);
        }

        return fields;
    }

    /**
     * Create transaction from matched message
     */
    private Transaction createTransaction(SwiftMessageDocument message, MessageTemplate template,
                                         Map<String, Object> extractedData, double confidence,
                                         Map<String, Double> fieldConfidences) {
        // Build matching details
        Transaction.MatchingDetails matchingDetails = Transaction.MatchingDetails.builder()
                .primaryTemplateId(template.getId())
                .fieldConfidences(fieldConfidences)
                .build();

        return Transaction.builder()
                .swiftMessageId(message.getId())
                .templateId(template.getId())
                .messageType(message.getMessageType())
                .extractedData(extractedData)
                .userEnteredData(new HashMap<>())
                .matchConfidence(confidence)
                .matchingDetails(matchingDetails)
                .processedAt(LocalDateTime.now())
                .processedBy("system")
                .status(confidence >= 0.95 ? Transaction.TransactionStatus.MATCHED :
                        Transaction.TransactionStatus.PENDING)
                .buyerId(message.getReceiverId())
                .sellerId(message.getSenderId())
                .metadata(new HashMap<>())
                .build();
    }

    /**
     * Format amount string
     */
    private String formatAmount(String amount) {
        try {
            String cleaned = amount.replaceAll("[^0-9.]", "");
            double value = Double.parseDouble(cleaned);
            return String.format("%.2f", value);
        } catch (Exception e) {
            return amount;
        }
    }

    /**
     * Format date string
     */
    private String formatDate(String date) {
        // Simple date formatting - can be enhanced
        return date.replaceAll("[^0-9]", "");
    }

    /**
     * Validate code format
     */
    private boolean validateCode(String code) {
        // Basic validation - can be enhanced with specific rules
        return code != null && code.matches("[A-Z0-9]+");
    }

    /**
     * Get all transactions for a template
     */
    public List<Transaction> getTransactionsByTemplate(String templateId) {
        return transactionRepository.findByTemplateId(templateId);
    }

    /**
     * Update transaction with user-entered data
     */
    public Transaction updateTransaction(String transactionId, Map<String, Object> userData) {
        Optional<Transaction> optTransaction = transactionRepository.findById(transactionId);

        if (optTransaction.isEmpty()) {
            throw new IllegalArgumentException("Transaction not found: " + transactionId);
        }

        Transaction transaction = optTransaction.get();
        transaction.setUserEnteredData(userData);
        transaction.setStatus(Transaction.TransactionStatus.VALIDATED);

        return transactionRepository.save(transaction);
    }

    /**
     * Analyze raw content against a template - used for Playground preview
     * This performs AI comparison without saving a transaction
     */
    public com.tradefinance.templateextraction.dto.StructuredAnalysis analyzeContentAgainstTemplate(
            String rawContent, String templateId) {
        log.info("Analyzing raw content against template {}", templateId);

        // Get template
        MessageTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));

        // Extract fields from raw content
        Map<String, Object> extractedFields = parseSwiftFieldsToMap(rawContent);

        // Generate structured analysis
        return llmComparisonService.getStructuredAnalysis(
                rawContent,
                template.getTemplateContent(),
                extractedFields
        );
    }

    /**
     * Parse SWIFT content fields into a map (helper method)
     */
    private Map<String, Object> parseSwiftFieldsToMap(String content) {
        Map<String, Object> fields = new HashMap<>();

        // Extract SWIFT field tags
        Pattern fieldPattern = Pattern.compile(":([0-9]{2}[A-Z]?):(.*?)(?=\\n:|$)", Pattern.DOTALL);
        Matcher matcher = fieldPattern.matcher(content);

        while (matcher.find()) {
            String fieldTag = matcher.group(1);
            String value = matcher.group(2).trim();
            fields.put(fieldTag, value);
        }

        return fields;
    }

    /**
     * Re-analyze a transaction - re-run the AI flow to update matching and extraction
     * This updates the existing transaction in-place without creating duplicates
     */
    public TemplateMatchResponse reanalyzeTransaction(String transactionId) {
        log.info("Re-analyzing transaction {}", transactionId);

        // Get existing transaction
        Transaction existingTransaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));

        // Get the original message
        SwiftMessageDocument message = messageRepository.findById(existingTransaction.getSwiftMessageId())
                .orElseThrow(() -> new IllegalArgumentException("Message not found: " + existingTransaction.getSwiftMessageId()));

        // Get message embedding from VectorService
        Optional<VectorEmbedding> vectorOpt = vectorService.getVector(message.getId());
        if (vectorOpt.isEmpty()) {
            log.error("No embedding found for message {}", message.getId());
            return TemplateMatchResponse.builder()
                    .messageType(message.getMessageType())
                    .matchConfidence(0.0)
                    .requiresManualReview(true)
                    .message("No embedding found for message")
                    .build();
        }

        float[] messageEmbedding = vectorOpt.get().getEmbedding();

        // Get templates for specific trading pair
        List<MessageTemplate> templates = templateRepository
                .findByMessageTypeAndBuyerIdAndSellerIdOrderByConfidenceDesc(
                        message.getMessageType(),
                        message.getSenderId(),
                        message.getReceiverId()
                );

        if (templates.isEmpty()) {
            log.warn("No templates found for trading pair {} -> {} (type: {})",
                    message.getSenderId(), message.getReceiverId(), message.getMessageType());
            return TemplateMatchResponse.builder()
                    .messageType(message.getMessageType())
                    .matchConfidence(0.0)
                    .requiresManualReview(true)
                    .message(String.format("No templates found for trading pair %s -> %s (type: %s)",
                            message.getSenderId(), message.getReceiverId(), message.getMessageType()))
                    .build();
        }

        // Find best matching template
        MessageTemplate bestTemplate = null;
        double bestSimilarity = 0.0;

        for (MessageTemplate template : templates) {
            double similarity = embeddingService.cosineSimilarity(
                    messageEmbedding,
                    template.getCentroidEmbedding()
            );

            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestTemplate = template;
            }
        }

        if (bestTemplate == null || bestSimilarity < similarityThreshold) {
            return TemplateMatchResponse.builder()
                    .messageType(message.getMessageType())
                    .matchConfidence(bestSimilarity)
                    .requiresManualReview(true)
                    .message("No suitable template found (similarity: " + bestSimilarity + ")")
                    .build();
        }

        // Extract data based on template
        Map<String, Object> extractedFields = extractFieldsFromMessage(message, bestTemplate);
        Map<String, Object> suggestedValues = generateSuggestedValues(bestTemplate, extractedFields);

        // Calculate field-level confidences
        Map<String, Double> fieldConfidences = calculateFieldConfidences(message, bestTemplate, extractedFields);

        // Generate LLM comparison (text format)
        String llmComparison = llmComparisonService.compareTransactionToTemplate(
                message.getRawContent(),
                bestTemplate.getTemplateContent(),
                extractedFields
        );

        // Generate structured analysis
        com.tradefinance.templateextraction.dto.StructuredAnalysis structuredAnalysis =
                llmComparisonService.getStructuredAnalysis(
                        message.getRawContent(),
                        bestTemplate.getTemplateContent(),
                        extractedFields
                );

        // Update the EXISTING transaction with new results (don't create a new one)
        existingTransaction.setTemplateId(bestTemplate.getId());
        existingTransaction.setMatchConfidence(bestSimilarity);
        existingTransaction.setExtractedData(extractedFields);  // Use extractedData, not extractedFields
        existingTransaction.setLlmComparison(llmComparison);
        existingTransaction.setStructuredAnalysis(structuredAnalysis);
        existingTransaction.setProcessedAt(LocalDateTime.now());

        // Update matching details with field confidences
        Transaction.MatchingDetails matchingDetails = existingTransaction.getMatchingDetails();
        if (matchingDetails == null) {
            matchingDetails = Transaction.MatchingDetails.builder()
                    .primaryTemplateId(bestTemplate.getId())
                    .fieldConfidences(fieldConfidences)
                    .build();
        } else {
            matchingDetails.setPrimaryTemplateId(bestTemplate.getId());
            matchingDetails.setFieldConfidences(fieldConfidences);
        }
        existingTransaction.setMatchingDetails(matchingDetails);

        // Update status based on confidence
        existingTransaction.setStatus(bestSimilarity >= 0.95 ?
                Transaction.TransactionStatus.MATCHED :
                Transaction.TransactionStatus.PENDING);

        // Preserve user-entered data (don't overwrite it)
        // User data is already in existingTransaction, so we don't need to do anything

        // Add metadata about re-analysis
        Map<String, String> metadata = existingTransaction.getMetadata();
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        metadata.put("lastReanalyzedAt", LocalDateTime.now().toString());
        metadata.put("reanalysisCount", String.valueOf(
                Integer.parseInt(metadata.getOrDefault("reanalysisCount", "0")) + 1
        ));
        existingTransaction.setMetadata(metadata);

        // Save the updated transaction (not a new one)
        transactionRepository.save(existingTransaction);

        // Update message
        message.setTemplateId(bestTemplate.getId());
        message.setStatus(SwiftMessageDocument.ProcessingStatus.TEMPLATE_MATCHED);
        messageRepository.save(message);

        log.info("Successfully re-analyzed transaction {}. New confidence: {}, Template: {}",
                transactionId, bestSimilarity, bestTemplate.getId());

        return TemplateMatchResponse.builder()
                .templateId(bestTemplate.getId())
                .messageType(message.getMessageType())
                .matchConfidence(bestSimilarity)
                .extractedFields(extractedFields)
                .suggestedValues(suggestedValues)
                .llmComparison(llmComparison)
                .requiresManualReview(bestSimilarity < 0.95)
                .message("Successfully re-analyzed transaction with template " + bestTemplate.getId())
                .build();
    }
}
