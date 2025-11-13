package com.tradefinance.templateextraction.controller;

import com.tradefinance.templateextraction.dto.SwiftMessageUploadRequest;
import com.tradefinance.templateextraction.model.SwiftMessage;
import com.tradefinance.templateextraction.service.SwiftMessageService;
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
@RequestMapping("/api/messages")
@Slf4j
public class SwiftMessageController {

    private final SwiftMessageService messageService;

    public SwiftMessageController(SwiftMessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping
    public ResponseEntity<SwiftMessage> uploadMessage(@Valid @RequestBody SwiftMessageUploadRequest request) {
        try {
            SwiftMessage message = messageService.uploadMessage(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(message);
        } catch (Exception e) {
            log.error("Error uploading message", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<List<SwiftMessage>> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            List<SwiftMessage> messages = messageService.uploadMessagesFromFile(file);
            return ResponseEntity.status(HttpStatus.CREATED).body(messages);
        } catch (Exception e) {
            log.error("Error uploading file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping
    public ResponseEntity<List<SwiftMessage>> getAllMessages() {
        List<SwiftMessage> messages = messageService.getAllMessages();
        return ResponseEntity.ok(messages);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SwiftMessage> getMessageById(@PathVariable String id) {
        return messageService.getMessageById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/type/{messageType}")
    public ResponseEntity<List<SwiftMessage>> getMessagesByType(@PathVariable String messageType) {
        List<SwiftMessage> messages = messageService.getMessagesByType(messageType);
        return ResponseEntity.ok(messages);
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<SwiftMessage>> getMessagesByStatus(@PathVariable String status) {
        try {
            SwiftMessage.ProcessingStatus processingStatus = SwiftMessage.ProcessingStatus.valueOf(status);
            List<SwiftMessage> messages = messageService.getMessagesByStatus(processingStatus);
            return ResponseEntity.ok(messages);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
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
