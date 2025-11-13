package com.tradefinance.templateextraction.controller;

import com.tradefinance.templateextraction.model.MessageTemplate;
import com.tradefinance.templateextraction.model.mongo.SwiftMessageDocument;
import com.tradefinance.templateextraction.repository.MessageTemplateRepository;
import com.tradefinance.templateextraction.repository.mongo.SwiftMessageMongoRepository;
import com.tradefinance.templateextraction.service.TemplateExtractionService;
import com.tradefinance.templateextraction.service.TemplateMatchingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/templates")
@Slf4j
public class TemplateController {

    private final TemplateExtractionService extractionService;
    private final MessageTemplateRepository templateRepository;
    private final SwiftMessageMongoRepository messageRepository;
    private final TemplateMatchingService matchingService;

    public TemplateController(TemplateExtractionService extractionService,
                            MessageTemplateRepository templateRepository,
                            SwiftMessageMongoRepository messageRepository,
                            TemplateMatchingService matchingService) {
        this.extractionService = extractionService;
        this.templateRepository = templateRepository;
        this.messageRepository = messageRepository;
        this.matchingService = matchingService;
    }

    @PostMapping("/extract")
    public ResponseEntity<Map<String, Object>> extractTemplates() {
        try {
            Map<String, Object> result = extractionService.extractTemplates();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error extracting templates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping
    public ResponseEntity<List<MessageTemplate>> getAllTemplates() {
        List<MessageTemplate> templates = new ArrayList<>();
        templateRepository.findAll().forEach(templates::add);
        return ResponseEntity.ok(templates);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MessageTemplate> getTemplateById(@PathVariable String id) {
        return templateRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/type/{messageType}")
    public ResponseEntity<List<MessageTemplate>> getTemplatesByType(@PathVariable String messageType) {
        List<MessageTemplate> templates = templateRepository.findByMessageType(messageType);
        return ResponseEntity.ok(templates);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable String id) {
        templateRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<List<SwiftMessageDocument>> getMessagesByTemplateId(@PathVariable String id) {
        try {
            List<SwiftMessageDocument> messages = messageRepository.findByTemplateId(id);
            log.info("Found {} messages for template {}", messages.size(), id);
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            log.error("Error fetching messages for template {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        long totalTemplates = templateRepository.count();

        Map<String, Object> stats = Map.of(
            "totalTemplates", totalTemplates,
            "templatesByType", Map.of(
                "MT700", templateRepository.countByMessageType("MT700"),
                "MT710", templateRepository.countByMessageType("MT710"),
                "MT720", templateRepository.countByMessageType("MT720")
            )
        );

        return ResponseEntity.ok(stats);
    }

    /**
     * Test a message content against all templates without saving
     * Used by the Playground feature
     */
    @PostMapping("/test-match")
    public ResponseEntity<Map<String, Object>> testMessageMatch(@RequestBody Map<String, String> request) {
        try {
            String rawContent = request.get("rawContent");
            String messageType = request.get("messageType");

            if (rawContent == null || rawContent.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "rawContent is required"));
            }

            if (messageType == null || messageType.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "messageType is required"));
            }

            Map<String, Object> result = extractionService.testMessageAgainstTemplates(rawContent, messageType);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error testing message match", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Analyze raw content against a specific template - used for Playground preview
     * Performs AI comparison without saving a transaction
     */
    @PostMapping("/analyze-content")
    public ResponseEntity<?> analyzeContent(@RequestBody Map<String, String> request) {
        try {
            String rawContent = request.get("rawContent");
            String templateId = request.get("templateId");

            if (rawContent == null || rawContent.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "rawContent is required"));
            }
            if (templateId == null || templateId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "templateId is required"));
            }

            com.tradefinance.templateextraction.dto.StructuredAnalysis analysis =
                    matchingService.analyzeContentAgainstTemplate(rawContent, templateId);

            return ResponseEntity.ok(analysis);
        } catch (IllegalArgumentException e) {
            log.error("Invalid request", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error analyzing content", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
