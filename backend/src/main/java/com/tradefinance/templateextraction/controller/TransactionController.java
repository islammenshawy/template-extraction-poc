package com.tradefinance.templateextraction.controller;

import com.tradefinance.templateextraction.dto.TemplateMatchResponse;
import com.tradefinance.templateextraction.model.mongo.SwiftMessageDocument;
import com.tradefinance.templateextraction.model.Transaction;
import com.tradefinance.templateextraction.repository.mongo.SwiftMessageMongoRepository;
import com.tradefinance.templateextraction.repository.TransactionRepository;
import com.tradefinance.templateextraction.service.TemplateMatchingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transactions")
@Slf4j
public class TransactionController {

    private final TransactionRepository transactionRepository;
    private final SwiftMessageMongoRepository messageRepository;
    private final TemplateMatchingService matchingService;

    public TransactionController(TransactionRepository transactionRepository,
                                SwiftMessageMongoRepository messageRepository,
                                TemplateMatchingService matchingService) {
        this.transactionRepository = transactionRepository;
        this.messageRepository = messageRepository;
        this.matchingService = matchingService;
    }

    @PostMapping("/match/{messageId}")
    public ResponseEntity<TemplateMatchResponse> matchMessage(@PathVariable String messageId) {
        try {
            SwiftMessageDocument message = messageRepository.findById(messageId)
                    .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));

            TemplateMatchResponse response = matchingService.matchMessageToTemplate(message);
            messageRepository.save(message);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error matching message", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "processedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection) {

        try {
            // Create Sort object
            Sort.Direction direction = sortDirection.equalsIgnoreCase("asc")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
            Sort sort = Sort.by(direction, sortBy);

            // Create Pageable object
            Pageable pageable = PageRequest.of(page, size, sort);

            // Get paginated results
            Page<Transaction> transactionPage = transactionRepository.findAll(pageable);

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("transactions", transactionPage.getContent());
            response.put("currentPage", transactionPage.getNumber());
            response.put("totalItems", transactionPage.getTotalElements());
            response.put("totalPages", transactionPage.getTotalPages());
            response.put("pageSize", transactionPage.getSize());
            response.put("hasNext", transactionPage.hasNext());
            response.put("hasPrevious", transactionPage.hasPrevious());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching paginated transactions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Transaction> getTransactionById(@PathVariable String id) {
        return transactionRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/template/{templateId}")
    public ResponseEntity<List<Transaction>> getTransactionsByTemplate(@PathVariable String templateId) {
        List<Transaction> transactions = matchingService.getTransactionsByTemplate(templateId);
        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/message/{messageId}")
    public ResponseEntity<Transaction> getTransactionByMessageId(@PathVariable String messageId) {
        return transactionRepository.findBySwiftMessageId(messageId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/preview-match")
    public ResponseEntity<Map<String, Double>> previewMatchFieldConfidences(
            @RequestBody Map<String, String> request) {
        try {
            String messageId = request.get("messageId");
            String templateId = request.get("templateId");

            if (messageId == null || templateId == null) {
                return ResponseEntity.badRequest().build();
            }

            Map<String, Double> fieldConfidences = matchingService.previewMatchFieldConfidences(
                    messageId, templateId);
            return ResponseEntity.ok(fieldConfidences);
        } catch (IllegalArgumentException e) {
            log.error("Invalid request for preview match", e);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error during preview match", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<Transaction>> getTransactionsByStatus(@PathVariable String status) {
        try {
            Transaction.TransactionStatus transactionStatus = Transaction.TransactionStatus.valueOf(status);
            List<Transaction> transactions = transactionRepository.findByStatus(transactionStatus);
            return ResponseEntity.ok(transactions);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Transaction> updateTransaction(@PathVariable String id,
                                                         @RequestBody Map<String, Object> userData) {
        try {
            Transaction transaction = matchingService.updateTransaction(id, userData);
            return ResponseEntity.ok(transaction);
        } catch (Exception e) {
            log.error("Error updating transaction", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        long totalTransactions = transactionRepository.count();
        stats.put("totalTransactions", totalTransactions);

        Map<String, Long> byStatus = new HashMap<>();
        for (Transaction.TransactionStatus status : Transaction.TransactionStatus.values()) {
            long count = transactionRepository.countByStatus(status);
            if (count > 0) {
                byStatus.put(status.name(), count);
            }
        }
        stats.put("byStatus", byStatus);

        return ResponseEntity.ok(stats);
    }

    @PostMapping("/{id}/reanalyze")
    public ResponseEntity<TemplateMatchResponse> reanalyzeTransaction(@PathVariable String id) {
        try {
            log.info("Re-analyzing transaction {}", id);
            TemplateMatchResponse response = matchingService.reanalyzeTransaction(id);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Transaction or message not found for re-analysis: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error re-analyzing transaction {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
