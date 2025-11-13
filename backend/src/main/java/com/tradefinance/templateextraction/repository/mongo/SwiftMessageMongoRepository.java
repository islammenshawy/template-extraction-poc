package com.tradefinance.templateextraction.repository.mongo;

import com.tradefinance.templateextraction.model.mongo.SwiftMessageDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SwiftMessageMongoRepository extends MongoRepository<SwiftMessageDocument, String> {

    List<SwiftMessageDocument> findByMessageType(String messageType);

    List<SwiftMessageDocument> findByStatus(SwiftMessageDocument.ProcessingStatus status);

    List<SwiftMessageDocument> findBySenderIdAndReceiverId(String senderId, String receiverId);

    List<SwiftMessageDocument> findByClusterId(Integer clusterId);

    List<SwiftMessageDocument> findByTemplateId(String templateId);

    List<SwiftMessageDocument> findByTimestampBetween(LocalDateTime start, LocalDateTime end);

    long countByMessageType(String messageType);

    long countByStatus(SwiftMessageDocument.ProcessingStatus status);

    @Query("{ 'messageType': ?0, 'status': ?1 }")
    List<SwiftMessageDocument> findByMessageTypeAndStatus(String messageType, SwiftMessageDocument.ProcessingStatus status);

    @Query("{ 'senderId': ?0, 'timestamp': { $gte: ?1 } }")
    List<SwiftMessageDocument> findBySenderIdSinceDate(String senderId, LocalDateTime since);

    // Find messages without a template assigned
    @Query("{ 'templateId': null, 'status': { $in: ['EMBEDDED', 'CLUSTERED'] } }")
    List<SwiftMessageDocument> findUnmatchedMessages();

    // Find messages without a template assigned (paginated)
    @Query("{ 'templateId': null, 'status': { $in: ['EMBEDDED', 'CLUSTERED'] } }")
    Page<SwiftMessageDocument> findUnmatchedMessages(Pageable pageable);

    // Find messages by parsed field value
    @Query("{ 'parsedFields.?0': ?1 }")
    List<SwiftMessageDocument> findByParsedFieldValue(String fieldName, String value);
}
