package com.tradefinance.templateextraction.repository;

import com.tradefinance.templateextraction.model.SwiftMessage;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SwiftMessageRepository extends ElasticsearchRepository<SwiftMessage, String> {

    List<SwiftMessage> findByMessageType(String messageType);

    List<SwiftMessage> findByStatus(SwiftMessage.ProcessingStatus status);

    List<SwiftMessage> findBySenderIdAndReceiverId(String senderId, String receiverId);

    List<SwiftMessage> findByClusterId(Integer clusterId);

    List<SwiftMessage> findByTemplateId(String templateId);

    List<SwiftMessage> findByTimestampBetween(LocalDateTime start, LocalDateTime end);

    long countByMessageType(String messageType);
}
