package com.tradefinance.templateextraction.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class InviteRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;
    
    // Getters and setters
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
