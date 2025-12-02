package com.tradefinance.templateextraction.service;

import com.tradefinance.templateextraction.model.auth.User;
import com.tradefinance.templateextraction.repository.auth.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;

/**
 * Service for creating initial admin user during application startup
 * This is a one-time setup service that can be enabled via environment variables
 */
@Slf4j
@Service
public class AdminProvisioningService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Value("${app.admin.provisioning.enabled:false}")
    private boolean adminProvisioningEnabled;

    @Value("${app.admin.provisioning.default-email:admin@template-extraction.com}")
    private String defaultAdminEmail;

    @Value("${app.admin.provisioning.default-password:AdminPass123!}")
    private String defaultAdminPassword;

    /**
     * Create default admin user on startup if provisioning is enabled and user doesn't exist
     */
    @PostConstruct
    public void createDefaultAdmin() {
        if (!adminProvisioningEnabled) {
            log.info("Admin provisioning is disabled");
            return;
        }

        try {
            // Check if admin user already exists
            if (userRepository.findByEmail(defaultAdminEmail).isPresent()) {
                log.info("Admin user already exists: {}", defaultAdminEmail);
                return;
            }

            // Create admin user
            User adminUser = new User();
            adminUser.setEmail(defaultAdminEmail);
            adminUser.setPassword(passwordEncoder.encode(defaultAdminPassword));
            adminUser.setEnabled(true);
            adminUser.setCreatedAt(LocalDateTime.now());

            userRepository.save(adminUser);
            log.info("✅ Default admin user created successfully: {}", defaultAdminEmail);
            log.info("🔐 You can now login with email: {}", defaultAdminEmail);
            
        } catch (Exception e) {
            log.error("❌ Failed to create default admin user: {}", e.getMessage(), e);
        }
    }
}