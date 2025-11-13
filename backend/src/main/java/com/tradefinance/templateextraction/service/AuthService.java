package com.tradefinance.templateextraction.service;

import com.tradefinance.templateextraction.dto.auth.LoginResponse;
import com.tradefinance.templateextraction.dto.auth.UserResponse;
import com.tradefinance.templateextraction.model.auth.Invitation;
import com.tradefinance.templateextraction.model.auth.User;
import com.tradefinance.templateextraction.repository.auth.InvitationRepository;
import com.tradefinance.templateextraction.repository.auth.UserRepository;
import com.tradefinance.templateextraction.security.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InvitationRepository invitationRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private EmailService emailService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * Authenticate user and generate JWT token
     */
    public LoginResponse login(String email, String password) {
        log.info("Login attempt for email: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Login failed - user not found: {}", email);
                    return new UsernameNotFoundException("Invalid email or password");
                });

        if (!user.isEnabled()) {
            log.warn("Login failed - user account disabled: {}", email);
            throw new BadCredentialsException("Account is disabled");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            log.warn("Login failed - invalid password for: {}", email);
            throw new BadCredentialsException("Invalid email or password");
        }

        // Update last login time
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        String token = jwtUtil.generateToken(email);
        log.info("Login successful for: {}", email);

        return new LoginResponse(token, email);
    }

    /**
     * Register new user using invitation token
     */
    @Transactional
    public UserResponse register(String email, String password, String token) {
        log.info("Registration attempt for email: {}", email);

        // Check if user already exists
        if (userRepository.existsByEmail(email)) {
            log.warn("Registration failed - user already exists: {}", email);
            throw new IllegalArgumentException("User with this email already exists");
        }

        // Validate invitation token
        Invitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> {
                    log.warn("Registration failed - invalid invitation token");
                    return new IllegalArgumentException("Invalid invitation token");
                });

        if (invitation.isUsed()) {
            log.warn("Registration failed - invitation token already used: {}", token);
            throw new IllegalArgumentException("Invitation token has already been used");
        }

        if (invitation.isExpired()) {
            log.warn("Registration failed - invitation token expired: {}", token);
            throw new IllegalArgumentException("Invitation token has expired");
        }

        if (!invitation.getEmail().equalsIgnoreCase(email)) {
            log.warn("Registration failed - email mismatch. Expected: {}, Got: {}",
                     invitation.getEmail(), email);
            throw new IllegalArgumentException("Email does not match invitation");
        }

        // Create new user
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.now());

        user = userRepository.save(user);

        // Mark invitation as used
        invitation.setUsed(true);
        invitationRepository.save(invitation);

        log.info("Registration successful for: {}", email);

        return new UserResponse(user.getId(), user.getEmail(), user.isEnabled(), user.getCreatedAt());
    }

    /**
     * Send invitation to a new user
     */
    @Transactional
    public void sendInvitation(String email) {
        log.info("Sending invitation to: {}", email);

        // Check if user already exists
        if (userRepository.existsByEmail(email)) {
            log.warn("Invitation failed - user already exists: {}", email);
            throw new IllegalArgumentException("User with this email already exists");
        }

        // Check if there's an existing unused invitation
        invitationRepository.findByEmail(email).ifPresent(existingInvitation -> {
            if (!existingInvitation.isUsed() && !existingInvitation.isExpired()) {
                log.warn("Invitation failed - active invitation already exists for: {}", email);
                throw new IllegalArgumentException("An active invitation already exists for this email");
            }
        });

        // Create new invitation
        Invitation invitation = new Invitation(email);
        invitation = invitationRepository.save(invitation);

        // Send invitation email
        try {
            emailService.sendInvitation(email, invitation.getToken());
            log.info("Invitation sent successfully to: {}", email);
        } catch (Exception e) {
            log.error("Failed to send invitation email to: {}", email, e);
            // Delete the invitation if email fails
            invitationRepository.delete(invitation);
            throw new RuntimeException("Failed to send invitation email", e);
        }
    }

    /**
     * Get all users
     */
    public List<UserResponse> getAllUsers() {
        log.info("Fetching all users");
        return userRepository.findAll().stream()
                .map(user -> new UserResponse(
                        user.getId(),
                        user.getEmail(),
                        user.isEnabled(),
                        user.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Toggle user enabled/disabled status
     */
    public UserResponse toggleUserStatus(String userId, String currentUserEmail) {
        log.info("Toggling status for user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("Toggle failed - user not found: {}", userId);
                    return new UsernameNotFoundException("User not found");
                });

        // Prevent users from disabling themselves
        if (user.getEmail().equalsIgnoreCase(currentUserEmail)) {
            log.warn("User attempted to disable their own account: {}", currentUserEmail);
            throw new IllegalArgumentException("You cannot disable your own account");
        }

        user.setEnabled(!user.isEnabled());
        user = userRepository.save(user);

        log.info("User status toggled - ID: {}, Enabled: {}", userId, user.isEnabled());

        return new UserResponse(user.getId(), user.getEmail(), user.isEnabled(), user.getCreatedAt());
    }

    /**
     * Extract email from JWT token
     */
    public String extractEmailFromToken(String token) {
        return jwtUtil.extractEmail(token);
    }

    /**
     * Load user by email for authentication
     */
    public User loadUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }
}
