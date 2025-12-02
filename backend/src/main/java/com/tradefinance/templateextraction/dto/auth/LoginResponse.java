package com.tradefinance.templateextraction.dto.auth;

public class LoginResponse {
    private String token;
    private String email;
    
    public LoginResponse(String token, String email) {
        this.token = token;
        this.email = email;
    }
    
    // Getters
    public String getToken() { return token; }
    public String getEmail() { return email; }
}
