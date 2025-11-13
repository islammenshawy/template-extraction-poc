package com.tradefinance.templateextraction.repository.mongo;

import com.tradefinance.templateextraction.model.mongo.SystemConfigurationDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SystemConfigurationRepository extends MongoRepository<SystemConfigurationDocument, String> {

    Optional<SystemConfigurationDocument> findByConfigKey(String configKey);

    List<SystemConfigurationDocument> findByConfigGroup(String configGroup);

    @Query("{ 'configGroup': ?0, 'active': true }")
    List<SystemConfigurationDocument> findActiveByGroup(String configGroup);

    @Query("{ 'configKey': ?0, 'active': true }")
    Optional<SystemConfigurationDocument> findActiveByKey(String configKey);

    @Query("{ 'active': true }")
    List<SystemConfigurationDocument> findAllActive();

    @Query("{ 'configGroup': ?0, 'configKey': ?1, 'active': true }")
    Optional<SystemConfigurationDocument> findActiveByGroupAndKey(String configGroup, String configKey);

    List<SystemConfigurationDocument> findByConfigGroupOrderByPriorityDesc(String configGroup);
}
