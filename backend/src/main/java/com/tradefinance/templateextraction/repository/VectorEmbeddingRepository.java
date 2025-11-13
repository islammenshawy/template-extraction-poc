package com.tradefinance.templateextraction.repository;

import com.tradefinance.templateextraction.model.VectorEmbedding;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VectorEmbeddingRepository extends ElasticsearchRepository<VectorEmbedding, String> {

    List<VectorEmbedding> findByDocumentType(String documentType);

    Optional<VectorEmbedding> findByReferenceId(String referenceId);

    List<VectorEmbedding> findByClusterId(Integer clusterId);

    void deleteByReferenceId(String referenceId);

    long countByDocumentType(String documentType);

    long countByClusterId(Integer clusterId);
}
