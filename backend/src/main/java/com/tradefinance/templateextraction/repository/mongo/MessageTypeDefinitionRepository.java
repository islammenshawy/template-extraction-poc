package com.tradefinance.templateextraction.repository.mongo;

import com.tradefinance.templateextraction.model.mongo.MessageTypeDefinitionDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageTypeDefinitionRepository extends MongoRepository<MessageTypeDefinitionDocument, String> {

    Optional<MessageTypeDefinitionDocument> findByMessageType(String messageType);

    List<MessageTypeDefinitionDocument> findByCategory(String category);

    List<MessageTypeDefinitionDocument> findBySeriesCode(String seriesCode);

    @Query("{ 'active': true }")
    List<MessageTypeDefinitionDocument> findAllActive();

    @Query("{ 'messageType': ?0, 'active': true }")
    Optional<MessageTypeDefinitionDocument> findActiveByMessageType(String messageType);

    @Query("{ 'category': ?0, 'active': true }")
    List<MessageTypeDefinitionDocument> findActiveByCategory(String category);

    @Query("{ 'processingRules.enableClustering': true, 'active': true }")
    List<MessageTypeDefinitionDocument> findClusteringEnabled();

    @Query("{ 'processingRules.requireManualReview': true, 'active': true }")
    List<MessageTypeDefinitionDocument> findRequiringManualReview();
}
