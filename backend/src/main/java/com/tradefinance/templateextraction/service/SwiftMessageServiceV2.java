package com.tradefinance.templateextraction.service;

import com.tradefinance.templateextraction.dto.SwiftMessageUploadRequest;
import com.tradefinance.templateextraction.model.VectorEmbedding;
import com.tradefinance.templateextraction.model.mongo.SwiftMessageDocument;
import com.tradefinance.templateextraction.repository.mongo.SwiftMessageMongoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * V2 Service using MongoDB for storage and ElasticSearch for vectors
 */
@Service
@Slf4j
public class SwiftMessageServiceV2 {

    private final SwiftMessageMongoRepository messageRepository;
    private final VectorService vectorService;

    public SwiftMessageServiceV2(SwiftMessageMongoRepository messageRepository,
                                VectorService vectorService) {
        this.messageRepository = messageRepository;
        this.vectorService = vectorService;
    }

    /**
     * Upload and process a single SWIFT message
     */
    public SwiftMessageDocument uploadMessage(SwiftMessageUploadRequest request) {
        log.info("Processing SWIFT message of type: {}", request.getMessageType());

        // Parse message fields
        Map<String, String> parsedFields = parseSwiftMessage(request.getRawContent());

        // Create message document
        SwiftMessageDocument message = SwiftMessageDocument.builder()
                .messageType(request.getMessageType())
                .rawContent(request.getRawContent())
                .parsedFields(parsedFields)
                .senderId(request.getSenderId())
                .receiverId(request.getReceiverId())
                .timestamp(LocalDateTime.now())
                .status(SwiftMessageDocument.ProcessingStatus.NEW)
                .notes(request.getNotes())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy("system")
                .build();

        // Save to MongoDB
        message = messageRepository.save(message);

        // Generate and store vector embedding in ElasticSearch
        vectorService.storeVector(
                message.getId(),
                VectorEmbedding.DocumentType.MESSAGE.name(),
                request.getRawContent(),
                null
        );

        // Update status
        message.setStatus(SwiftMessageDocument.ProcessingStatus.EMBEDDED);
        message.setUpdatedAt(LocalDateTime.now());
        message = messageRepository.save(message);

        log.info("Message saved with ID: {}", message.getId());
        return message;
    }

    /**
     * Upload multiple messages from a file
     */
    public List<SwiftMessageDocument> uploadMessagesFromFile(MultipartFile file) throws IOException {
        log.info("Processing file: {}", file.getOriginalFilename());

        List<SwiftMessageDocument> messages = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            StringBuilder currentMessage = new StringBuilder();
            String messageType = null;

            String line;
            while ((line = reader.readLine()) != null) {
                // Check for message type header
                Pattern mtPattern = Pattern.compile("\\{1:.*\\}\\{2:.*?(MT\\d{3})");
                Matcher matcher = mtPattern.matcher(line);

                if (matcher.find()) {
                    // Save previous message if exists
                    if (currentMessage.length() > 0 && messageType != null) {
                        SwiftMessageDocument message = createMessageFromContent(messageType, currentMessage.toString());
                        messages.add(message);
                        currentMessage = new StringBuilder();
                    }
                    messageType = matcher.group(1);
                }

                currentMessage.append(line).append("\n");
            }

            // Save last message
            if (currentMessage.length() > 0 && messageType != null) {
                SwiftMessageDocument message = createMessageFromContent(messageType, currentMessage.toString());
                messages.add(message);
            }
        }

        log.info("Processed {} messages from file", messages.size());
        return messages;
    }

    /**
     * Create message entity from raw content
     */
    private SwiftMessageDocument createMessageFromContent(String messageType, String content) {
        Map<String, String> parsedFields = parseSwiftMessage(content);

        SwiftMessageDocument message = SwiftMessageDocument.builder()
                .messageType(messageType)
                .rawContent(content)
                .parsedFields(parsedFields)
                .senderId(parsedFields.getOrDefault("sender", "UNKNOWN"))
                .receiverId(parsedFields.getOrDefault("receiver", "UNKNOWN"))
                .timestamp(LocalDateTime.now())
                .status(SwiftMessageDocument.ProcessingStatus.NEW)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy("system")
                .build();

        // Save to MongoDB
        message = messageRepository.save(message);

        // Store vector
        vectorService.storeVector(
                message.getId(),
                VectorEmbedding.DocumentType.MESSAGE.name(),
                content,
                null
        );

        // Update status
        message.setStatus(SwiftMessageDocument.ProcessingStatus.EMBEDDED);
        message.setUpdatedAt(LocalDateTime.now());
        return messageRepository.save(message);
    }

    /**
     * Parse SWIFT message fields
     */
    private Map<String, String> parseSwiftMessage(String content) {
        Map<String, String> fields = new HashMap<>();

        // Extract SWIFT field tags (format :TAG:VALUE)
        Pattern fieldPattern = Pattern.compile(":([0-9]{2}[A-Z]?):(.*?)(?=\\n:|$)", Pattern.DOTALL);
        Matcher matcher = fieldPattern.matcher(content);

        while (matcher.find()) {
            String tag = matcher.group(1);
            String value = matcher.group(2).trim();
            fields.put(tag, value);
        }

        // Extract sender/receiver from header blocks if present
        Pattern senderPattern = Pattern.compile("\\{1:[^}]*\\}\\{2:.*?([A-Z]{8})");
        Matcher senderMatcher = senderPattern.matcher(content);
        if (senderMatcher.find()) {
            fields.put("sender", senderMatcher.group(1));
        }

        Pattern receiverPattern = Pattern.compile("\\{2:.*?([A-Z]{8}).*?\\}");
        Matcher receiverMatcher = receiverPattern.matcher(content);
        if (receiverMatcher.find()) {
            fields.put("receiver", receiverMatcher.group(1));
        }

        return fields;
    }

    /**
     * Get all messages
     */
    public List<SwiftMessageDocument> getAllMessages() {
        return messageRepository.findAll();
    }

    /**
     * Get message by ID
     */
    public Optional<SwiftMessageDocument> getMessageById(String id) {
        return messageRepository.findById(id);
    }

    /**
     * Get messages by type
     */
    public List<SwiftMessageDocument> getMessagesByType(String messageType) {
        return messageRepository.findByMessageType(messageType);
    }

    /**
     * Get messages by status
     */
    public List<SwiftMessageDocument> getMessagesByStatus(SwiftMessageDocument.ProcessingStatus status) {
        return messageRepository.findByStatus(status);
    }

    /**
     * Delete message
     */
    public void deleteMessage(String id) {
        messageRepository.deleteById(id);
        vectorService.deleteVector(id);
        log.info("Deleted message: {}", id);
    }

    /**
     * Get message statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        long totalMessages = messageRepository.count();
        stats.put("totalMessages", totalMessages);

        // Count by message type
        Map<String, Long> byType = new HashMap<>();
        for (String type : Arrays.asList("MT700", "MT710", "MT720", "MT730", "MT740", "MT750")) {
            long count = messageRepository.countByMessageType(type);
            if (count > 0) {
                byType.put(type, count);
            }
        }
        stats.put("byMessageType", byType);

        // Count by status
        Map<String, Long> byStatus = new HashMap<>();
        for (SwiftMessageDocument.ProcessingStatus status : SwiftMessageDocument.ProcessingStatus.values()) {
            long count = messageRepository.countByStatus(status);
            if (count > 0) {
                byStatus.put(status.name(), count);
            }
        }
        stats.put("byStatus", byStatus);

        return stats;
    }

    /**
     * Get unmatched messages with pagination
     * Returns messages that don't have a template assigned (status EMBEDDED or CLUSTERED)
     */
    public Page<SwiftMessageDocument> getUnmatchedMessages(Pageable pageable) {
        return messageRepository.findUnmatchedMessages(pageable);
    }

    /**
     * Update message cluster assignment
     */
    public void updateMessageCluster(String messageId, Integer clusterId) {
        Optional<SwiftMessageDocument> optMessage = messageRepository.findById(messageId);
        if (optMessage.isPresent()) {
            SwiftMessageDocument message = optMessage.get();
            message.setClusterId(clusterId);
            message.setStatus(SwiftMessageDocument.ProcessingStatus.CLUSTERED);
            message.setUpdatedAt(LocalDateTime.now());
            messageRepository.save(message);

            // Update vector cluster
            vectorService.updateVector(messageId, message.getRawContent(), clusterId);
        }
    }

    /**
     * Update message template assignment
     */
    public void updateMessageTemplate(String messageId, String templateId) {
        Optional<SwiftMessageDocument> optMessage = messageRepository.findById(messageId);
        if (optMessage.isPresent()) {
            SwiftMessageDocument message = optMessage.get();
            message.setTemplateId(templateId);
            message.setStatus(SwiftMessageDocument.ProcessingStatus.TEMPLATE_MATCHED);
            message.setUpdatedAt(LocalDateTime.now());
            messageRepository.save(message);
        }
    }
}
