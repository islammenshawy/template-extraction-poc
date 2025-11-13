package com.tradefinance.templateextraction.model.auth;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Document(collection = "invitations")
public class Invitation {
    @Id
    private String id;

    @Indexed(unique = true)
    private String email;

    @Indexed(unique = true)
    private String token;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime expiresAt;

    private boolean used = false;

    public Invitation() {
        this.token = UUID.randomUUID().toString();
        this.expiresAt = LocalDateTime.now().plusDays(7); // 7 days expiry
    }

    public Invitation(String email) {
        this();
        this.email = email;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
