package com.tradefinance.templateextraction.controller;

import com.tradefinance.templateextraction.model.auth.User;
import com.tradefinance.templateextraction.repository.auth.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Secure admin provisioning controller
 * Creates initial admin user using a secret token from environment variables
 * This should be disabled after initial setup
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
public class AdminProvisioningController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Value("${ADMIN_PROVISIONING_TOKEN:}")
    private String adminProvisioningToken;

    @Value("${ADMIN_PROVISIONING_ENABLED:false}")
    private boolean adminProvisioningEnabled;

    /**
     * Secure admin provisioning endpoint
     * Creates an admin user using a secret token
     */
    @PostMapping("/provision")
    public ResponseEntity<?> provisionAdminUser(
            @RequestHeader("X-Admin-Token") String token,
            @RequestBody Map<String, String> request) {
        
        // Check if provisioning is enabled
        if (!adminProvisioningEnabled) {
            log.warn("Admin provisioning attempted but disabled");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Admin provisioning is disabled"));
        }

        // Validate the secret token
        if (token == null || token.isEmpty() || !token.equals(adminProvisioningToken)) {
            log.warn("Invalid or missing admin token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or missing admin token"));
        }

        try {
            String email = request.get("email");
            String password = request.get("password");

            // Validate input
            if (email == null || email.isEmpty() || password == null || password.isEmpty()) {
                log.warn("Invalid input for admin provisioning - missing email or password");
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Email and password are required"));
            }

            // Check if user already exists
            Optional<User> existingUser = userRepository.findByEmail(email);
            if (existingUser.isPresent()) {
                log.warn("Admin user already exists with email: {}", email);
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "User with this email already exists"));
            }

            // Create admin user
            User adminUser = new User();
            adminUser.setEmail(email);
            adminUser.setPassword(passwordEncoder.encode(password));
            adminUser.setEnabled(true);
            adminUser.setCreatedAt(LocalDateTime.now());

            User savedUser = userRepository.save(adminUser);
            
            log.info("✅ Admin user created successfully: {}", email);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Admin user created successfully");
            response.put("userId", savedUser.getId());
            response.put("email", savedUser.getEmail());
            response.put("enabled", savedUser.isEnabled());
            response.put("createdAt", savedUser.getCreatedAt());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Failed to create admin user: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create admin user: " + e.getMessage()));
        }
    }

    /**
     * Check if admin provisioning is enabled and configured
     */
    @GetMapping("/status")
    public ResponseEntity<?> getProvisioningStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("enabled", adminProvisioningEnabled);
        response.put("configured", !adminProvisioningToken.isEmpty());
        response.put("message", adminProvisioningEnabled ? 
            "Admin provisioning is enabled" : "Admin provisioning is disabled");
        
        if (adminProvisioningEnabled && adminProvisioningToken.isEmpty()) {
            response.put("warning", "Admin provisioning is enabled but no token is configured");
        }
        
        return ResponseEntity.ok(response);
    }
}