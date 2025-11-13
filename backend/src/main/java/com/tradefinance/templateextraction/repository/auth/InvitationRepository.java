package com.tradefinance.templateextraction.repository.auth;

import com.tradefinance.templateextraction.model.auth.Invitation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InvitationRepository extends MongoRepository<Invitation, String> {
    Optional<Invitation> findByToken(String token);
    Optional<Invitation> findByEmail(String email);
    boolean existsByEmail(String email);
}
