package com.tradefinance.templateextraction.controller;

import com.tradefinance.templateextraction.dto.auth.*;
import com.tradefinance.templateextraction.service.AuthService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    /**
     * POST /api/auth/login
     * Public endpoint - User login
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            log.info("Login request received for: {}", request.getEmail());
            LoginResponse response = authService.login(request.getEmail(), request.getPassword());
            return ResponseEntity.ok(response);
        } catch (UsernameNotFoundException | BadCredentialsException e) {
            log.warn("Login failed for {}: {}", request.getEmail(), e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(createErrorResponse("Invalid email or password"));
        } catch (Exception e) {
            log.error("Login error for {}: {}", request.getEmail(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("An error occurred during login"));
        }
    }

    /**
     * POST /api/auth/register?token=xxx
     * Public endpoint - User registration with invitation token
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            log.info("Registration request received for: {}", request.getEmail());
            UserResponse response = authService.register(
                    request.getEmail(),
                    request.getPassword(),
                    request.getToken()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.warn("Registration failed for {}: {}", request.getEmail(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(createErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Registration error for {}: {}", request.getEmail(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("An error occurred during registration"));
        }
    }

    /**
     * POST /api/auth/invite
     * Protected endpoint - Send invitation to new user
     */
    @PostMapping("/invite")
    public ResponseEntity<?> invite(@Valid @RequestBody InviteRequest request) {
        try {
            log.info("Invitation request received for: {}", request.getEmail());
            authService.sendInvitation(request.getEmail());
            return ResponseEntity.ok(createSuccessResponse("Invitation sent successfully"));
        } catch (IllegalArgumentException e) {
            log.warn("Invitation failed for {}: {}", request.getEmail(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(createErrorResponse(e.getMessage()));
        } catch (RuntimeException e) {
            log.error("Invitation error for {}: {}", request.getEmail(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to send invitation"));
        } catch (Exception e) {
            log.error("Unexpected error sending invitation to {}: {}", request.getEmail(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("An unexpected error occurred"));
        }
    }

    /**
     * GET /api/auth/users
     * Protected endpoint - Get all users
     */
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers() {
        try {
            log.info("Fetching all users");
            List<UserResponse> users = authService.getAllUsers();
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            log.error("Error fetching users: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to fetch users"));
        }
    }

    /**
     * PUT /api/auth/users/{id}/toggle
     * Protected endpoint - Toggle user enabled/disabled status
     */
    @PutMapping("/users/{id}/toggle")
    public ResponseEntity<?> toggleUserStatus(
            @PathVariable String id,
            @RequestHeader("Authorization") String authHeader) {
        try {
            log.info("Toggling status for user: {}", id);

            // Extract current user email from JWT token
            String currentUserEmail = extractEmailFromToken(authHeader);

            UserResponse response = authService.toggleUserStatus(id, currentUserEmail);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Cannot toggle user status: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(createErrorResponse(e.getMessage()));
        } catch (UsernameNotFoundException e) {
            log.warn("User not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(createErrorResponse("User not found"));
        } catch (Exception e) {
            log.error("Error toggling user status for {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to update user status"));
        }
    }

    /**
     * Helper method to extract email from JWT token
     */
    private String extractEmailFromToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return authService.extractEmailFromToken(token);
        }
        throw new IllegalArgumentException("Invalid authorization header");
    }

    /**
     * Helper method to create error response
     */
    private Map<String, String> createErrorResponse(String message) {
        Map<String, String> response = new HashMap<>();
        response.put("error", message);
        return response;
    }

    /**
     * Helper method to create success response
     */
    private Map<String, String> createSuccessResponse(String message) {
        Map<String, String> response = new HashMap<>();
        response.put("message", message);
        return response;
    }

    /**
     * Global exception handler for validation errors
     */
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationExceptions(
            org.springframework.web.bind.MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );
        log.warn("Validation errors: {}", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }
}
