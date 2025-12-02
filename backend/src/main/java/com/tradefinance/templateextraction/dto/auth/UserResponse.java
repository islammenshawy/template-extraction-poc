package com.tradefinance.templateextraction.dto.auth;

import java.time.LocalDateTime;

public class UserResponse {
    private String id;
    private String email;
    private boolean enabled;
    private LocalDateTime createdAt;
    
    public UserResponse(String id, String email, boolean enabled, LocalDateTime createdAt) {
        this.id = id;
        this.email = email;
        this.enabled = enabled;
        this.createdAt = createdAt;
    }
    
    // Getters
    public String getId() { return id; }
    public String getEmail() { return email; }
    public boolean isEnabled() { return enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
