package com.tradefinance.templateextraction.service;

import com.tradefinance.templateextraction.dto.SwiftMessageUploadRequest;
import com.tradefinance.templateextraction.model.SwiftMessage;
import com.tradefinance.templateextraction.repository.SwiftMessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class SwiftMessageService {

    private final SwiftMessageRepository messageRepository;
    private final EmbeddingService embeddingService;

    public SwiftMessageService(SwiftMessageRepository messageRepository,
                              EmbeddingService embeddingService) {
        this.messageRepository = messageRepository;
        this.embeddingService = embeddingService;
    }

    /**
     * Upload and process a single SWIFT message
     */
    public SwiftMessage uploadMessage(SwiftMessageUploadRequest request) {
        log.info("Processing SWIFT message of type: {}", request.getMessageType());

        // Parse message fields
        Map<String, String> parsedFields = parseSwiftMessage(request.getRawContent());

        // Generate embedding
        float[] embedding = embeddingService.generateEmbedding(request.getRawContent());

        // Create message entity
        SwiftMessage message = SwiftMessage.builder()
                .messageType(request.getMessageType())
                .rawContent(request.getRawContent())
                .parsedFields(parsedFields)
                .senderId(request.getSenderId())
                .receiverId(request.getReceiverId())
                .timestamp(LocalDateTime.now())
                .embedding(embedding)
                .status(SwiftMessage.ProcessingStatus.EMBEDDED)
                .notes(request.getNotes())
                .build();

        return messageRepository.save(message);
    }

    /**
     * Upload multiple messages from a file
     */
    public List<SwiftMessage> uploadMessagesFromFile(MultipartFile file) throws IOException {
        log.info("Processing file: {}", file.getOriginalFilename());

        List<SwiftMessage> messages = new ArrayList<>();

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
                        SwiftMessage message = createMessageFromContent(messageType, currentMessage.toString());
                        messages.add(messageRepository.save(message));
                        currentMessage = new StringBuilder();
                    }
                    messageType = matcher.group(1);
                }

                currentMessage.append(line).append("\n");
            }

            // Save last message
            if (currentMessage.length() > 0 && messageType != null) {
                SwiftMessage message = createMessageFromContent(messageType, currentMessage.toString());
                messages.add(messageRepository.save(message));
            }
        }

        log.info("Processed {} messages from file", messages.size());
        return messages;
    }

    /**
     * Create message entity from raw content
     */
    private SwiftMessage createMessageFromContent(String messageType, String content) {
        Map<String, String> parsedFields = parseSwiftMessage(content);
        float[] embedding = embeddingService.generateEmbedding(content);

        return SwiftMessage.builder()
                .messageType(messageType)
                .rawContent(content)
                .parsedFields(parsedFields)
                .senderId(parsedFields.getOrDefault("sender", "UNKNOWN"))
                .receiverId(parsedFields.getOrDefault("receiver", "UNKNOWN"))
                .timestamp(LocalDateTime.now())
                .embedding(embedding)
                .status(SwiftMessage.ProcessingStatus.EMBEDDED)
                .build();
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
    public List<SwiftMessage> getAllMessages() {
        List<SwiftMessage> messages = new ArrayList<>();
        messageRepository.findAll().forEach(messages::add);
        return messages;
    }

    /**
     * Get message by ID
     */
    public Optional<SwiftMessage> getMessageById(String id) {
        return messageRepository.findById(id);
    }

    /**
     * Get messages by type
     */
    public List<SwiftMessage> getMessagesByType(String messageType) {
        return messageRepository.findByMessageType(messageType);
    }

    /**
     * Get messages by status
     */
    public List<SwiftMessage> getMessagesByStatus(SwiftMessage.ProcessingStatus status) {
        return messageRepository.findByStatus(status);
    }

    /**
     * Delete message
     */
    public void deleteMessage(String id) {
        messageRepository.deleteById(id);
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
        for (SwiftMessage.ProcessingStatus status : SwiftMessage.ProcessingStatus.values()) {
            long count = messageRepository.findByStatus(status).size();
            if (count > 0) {
                byStatus.put(status.name(), count);
            }
        }
        stats.put("byStatus", byStatus);

        return stats;
    }
}
