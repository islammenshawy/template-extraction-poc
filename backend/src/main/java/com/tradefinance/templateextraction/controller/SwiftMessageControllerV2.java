package com.tradefinance.templateextraction.controller;

import com.tradefinance.templateextraction.dto.SwiftMessageUploadRequest;
import com.tradefinance.templateextraction.model.mongo.SwiftMessageDocument;
import com.tradefinance.templateextraction.service.SwiftMessageServiceV2;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/messages")
@Slf4j
public class SwiftMessageControllerV2 {

    private final SwiftMessageServiceV2 messageService;

    public SwiftMessageControllerV2(SwiftMessageServiceV2 messageService) {
        this.messageService = messageService;
    }

    @PostMapping
    public ResponseEntity<SwiftMessageDocument> uploadMessage(@Valid @RequestBody SwiftMessageUploadRequest request) {
        try {
            SwiftMessageDocument message = messageService.uploadMessage(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(message);
        } catch (Exception e) {
            log.error("Error uploading message", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<List<SwiftMessageDocument>> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            List<SwiftMessageDocument> messages = messageService.uploadMessagesFromFile(file);
            return ResponseEntity.status(HttpStatus.CREATED).body(messages);
        } catch (Exception e) {
            log.error("Error uploading file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping
    public ResponseEntity<List<SwiftMessageDocument>> getAllMessages() {
        List<SwiftMessageDocument> messages = messageService.getAllMessages();
        return ResponseEntity.ok(messages);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SwiftMessageDocument> getMessageById(@PathVariable String id) {
        return messageService.getMessageById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/type/{messageType}")
    public ResponseEntity<List<SwiftMessageDocument>> getMessagesByType(@PathVariable String messageType) {
        List<SwiftMessageDocument> messages = messageService.getMessagesByType(messageType);
        return ResponseEntity.ok(messages);
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<SwiftMessageDocument>> getMessagesByStatus(@PathVariable String status) {
        try {
            SwiftMessageDocument.ProcessingStatus processingStatus =
                    SwiftMessageDocument.ProcessingStatus.valueOf(status);
            List<SwiftMessageDocument> messages = messageService.getMessagesByStatus(processingStatus);
            return ResponseEntity.ok(messages);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/unmatched")
    public ResponseEntity<Map<String, Object>> getUnmatchedMessages(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "timestamp") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection) {

        try {
            // Create Sort object
            Sort.Direction direction = sortDirection.equalsIgnoreCase("asc")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
            Sort sort = Sort.by(direction, sortBy);

            // Create Pageable object
            Pageable pageable = PageRequest.of(page, size, sort);

            // Get paginated unmatched messages
            Page<SwiftMessageDocument> messagePage = messageService.getUnmatchedMessages(pageable);

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("messages", messagePage.getContent());
            response.put("currentPage", messagePage.getNumber());
            response.put("totalItems", messagePage.getTotalElements());
            response.put("totalPages", messagePage.getTotalPages());
            response.put("pageSize", messagePage.getSize());
            response.put("hasNext", messagePage.hasNext());
            response.put("hasPrevious", messagePage.hasPrevious());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching paginated unmatched messages", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMessage(@PathVariable String id) {
        messageService.deleteMessage(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        Map<String, Object> stats = messageService.getStatistics();
        return ResponseEntity.ok(stats);
    }
}
