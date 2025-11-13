package com.tradefinance.templateextraction.repository.mongo;

import com.tradefinance.templateextraction.model.mongo.TransactionDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionMongoRepository extends MongoRepository<TransactionDocument, String> {

    List<TransactionDocument> findByTemplateId(String templateId);

    List<TransactionDocument> findByStatus(TransactionDocument.TransactionStatus status);

    Page<TransactionDocument> findByStatus(TransactionDocument.TransactionStatus status, Pageable pageable);

    List<TransactionDocument> findByBuyerIdOrSellerId(String buyerId, String sellerId);

    List<TransactionDocument> findByMessageType(String messageType);

    List<TransactionDocument> findByProcessedAtBetween(LocalDateTime start, LocalDateTime end);

    long countByStatus(TransactionDocument.TransactionStatus status);

    long countByTemplateId(String templateId);

    @Query("{ 'matchConfidence': { $gte: ?0 } }")
    List<TransactionDocument> findByMinimumConfidence(Double minConfidence);

    @Query("{ 'status': ?0, 'processedAtBetween': { $gte: ?1, $lte: ?2 } }")
    List<TransactionDocument> findByStatusAndDateRange(TransactionDocument.TransactionStatus status,
                                                        LocalDateTime start,
                                                        LocalDateTime end);

    @Query("{ 'amount': { $gte: ?0, $lte: ?1 } }")
    List<TransactionDocument> findByAmountRange(BigDecimal minAmount, BigDecimal maxAmount);

    @Query("{ 'currency': ?0, 'amount': { $gte: ?1 } }")
    List<TransactionDocument> findByCurrencyAndMinAmount(String currency, BigDecimal minAmount);

    // Find transactions requiring manual review
    @Query("{ 'matchConfidence': { $lt: ?0 }, 'status': 'PENDING' }")
    List<TransactionDocument> findRequiringManualReview(Double confidenceThreshold);

    // Find transactions by documentary credit number
    @Query("{ 'documentaryCreditNumber': ?0 }")
    List<TransactionDocument> findByDocumentaryCreditNumber(String dcNumber);

    // Find transactions with validation issues
    @Query("{ 'matchingDetails.warnings': { $exists: true, $ne: [] } }")
    List<TransactionDocument> findWithWarnings();

    // Statistics queries
    @Query(value = "{ 'status': 'COMPLETED' }", count = true)
    long countCompletedTransactions();

    @Query("{ 'processedAt': { $gte: ?0 }, 'status': 'COMPLETED' }")
    List<TransactionDocument> findCompletedSince(LocalDateTime since);

    // Workflow queries
    @Query("{ 'workflowState.currentStep': ?0 }")
    List<TransactionDocument> findByWorkflowStep(String step);

    @Query("{ 'workflowState.pendingSteps': { $elemMatch: { $eq: ?0 } } }")
    List<TransactionDocument> findWithPendingStep(String step);
}
