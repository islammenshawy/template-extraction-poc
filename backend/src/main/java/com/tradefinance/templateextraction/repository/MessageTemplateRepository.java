package com.tradefinance.templateextraction.repository;

import com.tradefinance.templateextraction.model.MessageTemplate;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageTemplateRepository extends ElasticsearchRepository<MessageTemplate, String> {

    List<MessageTemplate> findByMessageType(String messageType);

    Optional<MessageTemplate> findByClusterId(Integer clusterId);

    List<MessageTemplate> findByMessageTypeOrderByConfidenceDesc(String messageType);

    /**
     * Find templates for a specific trading pair (buyer-seller combination)
     * ordered by confidence descending
     */
    List<MessageTemplate> findByMessageTypeAndBuyerIdAndSellerIdOrderByConfidenceDesc(
            String messageType, String buyerId, String sellerId);

    long countByMessageType(String messageType);
}
