package com.tradefinance.templateextraction.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class UserResponse {
    private String id;
    private String email;
    private boolean enabled;
    private LocalDateTime createdAt;
}
