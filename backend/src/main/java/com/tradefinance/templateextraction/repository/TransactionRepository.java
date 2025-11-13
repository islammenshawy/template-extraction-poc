package com.tradefinance.templateextraction.repository;

import com.tradefinance.templateextraction.model.Transaction;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends ElasticsearchRepository<Transaction, String> {

    List<Transaction> findByTemplateId(String templateId);

    List<Transaction> findByStatus(Transaction.TransactionStatus status);

    List<Transaction> findByBuyerIdOrSellerId(String buyerId, String sellerId);

    List<Transaction> findByMessageType(String messageType);

    List<Transaction> findByProcessedAtBetween(LocalDateTime start, LocalDateTime end);

    Optional<Transaction> findBySwiftMessageId(String swiftMessageId);

    long countByStatus(Transaction.TransactionStatus status);

    long countByTemplateId(String templateId);
}
