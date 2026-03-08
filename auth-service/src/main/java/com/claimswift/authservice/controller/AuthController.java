package com.claimswift.authservice.controller;

import com.claimswift.authservice.dto.*;
import com.claimswift.authservice.service.AuthService;
import com.claimswift.authservice.util.StandardResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<StandardResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(StandardResponse.success("User registered successfully", response));
    }

    @PostMapping("/login")
    public ResponseEntity<StandardResponse<LoginChallengeResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginChallengeResponse response = authService.login(request);
        return ResponseEntity.ok(StandardResponse.success("OTP sent to your registered email", response));
    }

    @PostMapping("/login/verify-otp")
    public ResponseEntity<StandardResponse<AuthResponse>> verifyLoginOtp(
            @Valid @RequestBody VerifyLoginOtpRequest request) {
        AuthResponse response = authService.verifyLoginOtp(request);
        return ResponseEntity.ok(StandardResponse.success("Login successful", response));
    }

    @PostMapping("/logout")
    public ResponseEntity<StandardResponse<Void>> logout(HttpServletRequest request) {
        String token = extractTokenFromRequest(request);
        authService.logout(token);
        return ResponseEntity.ok(StandardResponse.success("Logout successful", null));
    }

    @PostMapping("/refresh")
    public ResponseEntity<StandardResponse<AuthResponse>> refresh(HttpServletRequest request) {
        String token = extractTokenFromRequest(request);
        AuthResponse response = authService.refreshToken(token);
        return ResponseEntity.ok(StandardResponse.success("Token refreshed", response));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('POLICYHOLDER', 'ADJUSTER', 'MANAGER', 'ADMIN')")
    public ResponseEntity<StandardResponse<UserDTO>> getCurrentUser(@RequestAttribute("userId") Long userId) {
        UserDTO user = authService.getCurrentUser(userId);
        return ResponseEntity.ok(StandardResponse.success(user));
    }

    @GetMapping("/adjusters")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<StandardResponse<List<UserDTO>>> getAdjusters() {
        return ResponseEntity.ok(StandardResponse.success(authService.getAdjusters()));
    }

    @GetMapping("/managers")
    @PreAuthorize("hasAnyRole('ADJUSTER', 'MANAGER', 'ADMIN')")
    public ResponseEntity<StandardResponse<List<UserDTO>>> getManagers() {
        return ResponseEntity.ok(StandardResponse.success(authService.getManagers()));
    }

    @GetMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StandardResponse<List<UserDTO>>> getAllUsers() {
        return ResponseEntity.ok(StandardResponse.success(authService.getAllUsers()));
    }

    @GetMapping("/admin/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StandardResponse<UserDTO>> getUserById(@PathVariable("id") Long userId) {
        return ResponseEntity.ok(StandardResponse.success(authService.getUserById(userId)));
    }

    @PostMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StandardResponse<UserDTO>> createInternalUser(
            @Valid @RequestBody AdminCreateUserRequest request,
            @RequestAttribute("username") String actorUsername) {
        UserDTO user = authService.createInternalUser(request, actorUsername);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(StandardResponse.success("User created successfully", user));
    }

    @PutMapping("/admin/users/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StandardResponse<UserDTO>> updateUserRoles(
            @PathVariable("id") Long userId,
            @Valid @RequestBody UserRoleUpdateRequest request,
            @RequestAttribute("username") String actorUsername) {
        UserDTO user = authService.updateUserRoles(userId, request.getRoles(), actorUsername);
        return ResponseEntity.ok(StandardResponse.success("User roles updated", user));
    }

    @PatchMapping("/admin/users/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StandardResponse<UserDTO>> updateUserStatus(
            @PathVariable("id") Long userId,
            @Valid @RequestBody UserStatusUpdateRequest request,
            @RequestAttribute("username") String actorUsername) {
        UserDTO user = authService.updateUserStatus(userId, request.getStatus(), actorUsername);
        return ResponseEntity.ok(StandardResponse.success("User status updated", user));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Auth Service is running");
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
