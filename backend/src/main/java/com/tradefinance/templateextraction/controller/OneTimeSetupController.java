package com.tradefinance.templateextraction.controller;

import com.tradefinance.templateextraction.model.auth.User;
import com.tradefinance.templateextraction.repository.auth.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * One-time setup controller for creating initial admin user
 * This should be disabled after initial setup
 */
@RestController
@RequestMapping("/api/setup")
public class OneTimeSetupController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Value("${ONE_TIME_SETUP_ENABLED:false}")
    private boolean oneTimeSetupEnabled;

    /**
     * Create initial admin user - one-time endpoint
     * Creates admin user with predefined credentials
     */
    @PostMapping("/create-admin")
    public ResponseEntity<?> createInitialAdmin() {
        
        // Log for debugging
        System.out.println("ONE_TIME_SETUP_ENABLED value: " + oneTimeSetupEnabled);
        
        // Check if one-time setup is enabled
        if (!oneTimeSetupEnabled) {
            System.out.println("ONE_TIME_SETUP_ENABLED is false - returning 403");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "One-time setup is disabled", "debug", "ONE_TIME_SETUP_ENABLED=" + oneTimeSetupEnabled));
        }

        try {
            String adminEmail = "admin@template-extraction.com";
            String adminPassword = "AdminPass123!";

            // Check if admin user already exists
            if (userRepository.findByEmail(adminEmail).isPresent()) {
                return ResponseEntity.ok(Map.of("message", "Admin user already exists"));
            }

            // Create admin user
            User adminUser = new User();
            adminUser.setEmail(adminEmail);
            adminUser.setPassword(passwordEncoder.encode(adminPassword));
            adminUser.setEnabled(true);
            adminUser.setCreatedAt(LocalDateTime.now());

            User savedUser = userRepository.save(adminUser);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Admin user created successfully");
            response.put("userId", savedUser.getId());
            response.put("email", savedUser.getEmail());
            response.put("enabled", savedUser.isEnabled());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create admin user: " + e.getMessage()));
        }
    }

    /**
     * Check if one-time setup is enabled
     */
    @GetMapping("/status")
    public ResponseEntity<?> getSetupStatus() {
        System.out.println("Setup status endpoint called - ONE_TIME_SETUP_ENABLED=" + oneTimeSetupEnabled);
        Map<String, Object> response = new HashMap<>();
        response.put("enabled", oneTimeSetupEnabled);
        response.put("message", oneTimeSetupEnabled ? 
            "One-time setup is enabled" : "One-time setup is disabled");
        response.put("debug_env_value", System.getenv("ONE_TIME_SETUP_ENABLED"));
        return ResponseEntity.ok(response);
    }
}