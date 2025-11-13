package com.tradefinance.templateextraction.repository.mongo;

import com.tradefinance.templateextraction.model.mongo.MessageTemplateDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageTemplateMongoRepository extends MongoRepository<MessageTemplateDocument, String> {

    List<MessageTemplateDocument> findByMessageType(String messageType);

    Optional<MessageTemplateDocument> findByClusterId(Integer clusterId);

    List<MessageTemplateDocument> findByMessageType(String messageType, Sort sort);

    long countByMessageType(String messageType);

    @Query("{ 'confidence': { $gte: ?0 } }")
    List<MessageTemplateDocument> findByMinimumConfidence(Double minConfidence);

    @Query("{ 'messageType': ?0, 'confidence': { $gte: ?1 } }")
    List<MessageTemplateDocument> findByMessageTypeAndMinConfidence(String messageType, Double minConfidence, Sort sort);

    @Query("{ 'lastUpdated': { $gte: ?0 } }")
    List<MessageTemplateDocument> findRecentlyUpdated(LocalDateTime since);

    @Query("{ 'statistics.totalMatches': { $gte: ?0 } }")
    List<MessageTemplateDocument> findByMinimumMatches(int minMatches);

    // Find templates that haven't been used recently
    @Query("{ 'statistics.lastMatchedAt': { $lt: ?0 } }")
    List<MessageTemplateDocument> findStaleTemplates(LocalDateTime lastMatchedBefore);

    // Find templates with high confidence
    @Query("{ 'confidence': { $gte: 0.9 } }")
    List<MessageTemplateDocument> findHighConfidenceTemplates();
}
