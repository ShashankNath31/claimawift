package com.claimswift.authservice.service;

import com.claimswift.authservice.dto.*;
import com.claimswift.authservice.entity.LoginOtpChallenge;
import com.claimswift.authservice.entity.Role;
import com.claimswift.authservice.entity.User;
import com.claimswift.authservice.repository.LoginOtpChallengeRepository;
import com.claimswift.authservice.repository.RoleRepository;
import com.claimswift.authservice.repository.UserRepository;
import com.claimswift.authservice.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.security.SecureRandom;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Authentication Service
 * Handles user registration, login, logout, and role management
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final LoginOtpChallengeRepository loginOtpChallengeRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final JavaMailSender mailSender;

    @Value("${auth.two-factor.otp-expiry-minutes:5}")
    private Long otpExpiryMinutes = 5L;

    @Value("${auth.two-factor.max-attempts:5}")
    private Integer otpMaxAttempts = 5;

    @Value("${auth.two-factor.mail-from:}")
    private String otpMailFrom;

    @Value("${auth.two-factor.enabled:true}")
    private boolean twoFactorEnabled = true;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        Role policyholderRole = roleRepository.findByName(Role.RoleName.ROLE_POLICYHOLDER)
                .orElseThrow(() -> new RuntimeException("Default policyholder role not found"));

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phoneNumber(request.getPhoneNumber())
                .roles(Set.of(policyholderRole))
                .status(User.UserStatus.ACTIVE)
                .build();

        User savedUser = userRepository.save(user);
        log.info("User registered successfully: {}", savedUser.getUsername());

        String token = generateToken(savedUser);

        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getExpirationTime())
                .user(mapToUserDTO(savedUser))
                .build();
    }

    @Transactional
    public UserDTO createInternalUser(AdminCreateUserRequest request, String actorUsername) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        Set<Role> roles = resolveRoles(request.getRoles());
        User.UserStatus status = resolveStatus(request.getStatus(), User.UserStatus.ACTIVE);

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phoneNumber(request.getPhoneNumber())
                .roles(roles)
                .status(status)
                .build();

        User saved = userRepository.save(user);
        log.info("Admin {} created internal user {} with roles {}", actorUsername, saved.getUsername(), request.getRoles());
        return mapToUserDTO(saved);
    }

    @Transactional
    public UserDTO updateUserRoles(Long userId, Set<String> roleNames, String actorUsername) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Set<Role> roles = resolveRoles(roleNames);
        user.setRoles(roles);
        User saved = userRepository.save(user);
        log.info("Admin {} updated roles for user {} to {}", actorUsername, saved.getUsername(), roleNames);
        return mapToUserDTO(saved);
    }

    @Transactional
    public UserDTO updateUserStatus(Long userId, String status, String actorUsername) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        User.UserStatus resolved = resolveStatus(status, user.getStatus());
        user.setStatus(resolved);
        User saved = userRepository.save(user);
        log.info("Admin {} updated status for user {} to {}", actorUsername, saved.getUsername(), resolved);
        return mapToUserDTO(saved);
    }

    @Transactional
    public LoginChallengeResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsernameOrEmail(),
                        request.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        User user = userRepository.findByUsernameOrEmail(request.getUsernameOrEmail(), request.getUsernameOrEmail())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!twoFactorEnabled) {
            throw new IllegalArgumentException("Two-factor authentication is disabled in this environment.");
        }

        LocalDateTime now = LocalDateTime.now();
        invalidatePendingChallenges(user.getId(), now);

        String otpCode = generateOtpCode();
        LocalDateTime expiresAt = now.plusMinutes(Math.max(1, otpExpiryMinutes));
        LoginOtpChallenge challenge = LoginOtpChallenge.builder()
                .challengeId(UUID.randomUUID().toString())
                .user(user)
                .otpHash(passwordEncoder.encode(otpCode))
                .expiresAt(expiresAt)
                .attemptCount(0)
                .consumed(false)
                .build();

        LoginOtpChallenge savedChallenge = loginOtpChallengeRepository.save(challenge);
        sendLoginOtpEmail(user, otpCode, expiresAt);

        log.info("OTP login challenge generated for user {}", user.getUsername());
        return LoginChallengeResponse.builder()
                .challengeId(savedChallenge.getChallengeId())
                .maskedEmail(maskEmail(user.getEmail()))
                .expiresInSeconds(ChronoUnit.SECONDS.between(now, expiresAt))
                .build();
    }

    @Transactional
    public AuthResponse verifyLoginOtp(VerifyLoginOtpRequest request) {
        LoginOtpChallenge challenge = loginOtpChallengeRepository.findByChallengeId(request.getChallengeId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired login challenge."));

        if (Boolean.TRUE.equals(challenge.getConsumed())) {
            throw new IllegalArgumentException("This OTP challenge has already been used.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (challenge.getExpiresAt().isBefore(now)) {
            challenge.setConsumed(true);
            loginOtpChallengeRepository.save(challenge);
            throw new IllegalArgumentException("OTP expired. Please login again to request a new OTP.");
        }

        int maxAttempts = Math.max(1, otpMaxAttempts == null ? 5 : otpMaxAttempts);
        int attempts = challenge.getAttemptCount() == null ? 0 : challenge.getAttemptCount();
        if (attempts >= maxAttempts) {
            challenge.setConsumed(true);
            loginOtpChallengeRepository.save(challenge);
            throw new IllegalArgumentException("Maximum OTP attempts exceeded. Please login again.");
        }

        if (!passwordEncoder.matches(request.getOtp(), challenge.getOtpHash())) {
            challenge.setAttemptCount(attempts + 1);
            if (challenge.getAttemptCount() >= maxAttempts) {
                challenge.setConsumed(true);
            }
            loginOtpChallengeRepository.save(challenge);
            throw new IllegalArgumentException("Invalid OTP.");
        }

        challenge.setConsumed(true);
        challenge.setVerifiedAt(now);
        loginOtpChallengeRepository.save(challenge);

        User user = challenge.getUser();
        user.setLastLoginAt(now);
        userRepository.save(user);

        String token = generateToken(user);
        log.info("User completed 2FA login successfully: {}", user.getUsername());

        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getExpirationTime())
                .user(mapToUserDTO(user))
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResponse refreshToken(String token) {
        if (!jwtTokenProvider.validateToken(token)) {
            throw new RuntimeException("Invalid token");
        }

        String username = jwtTokenProvider.getUsernameFromToken(token);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String newToken = generateToken(user);
        return AuthResponse.builder()
                .accessToken(newToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getExpirationTime())
                .user(mapToUserDTO(user))
                .build();
    }

    @Transactional
    public void logout(String token) {
        jwtTokenProvider.invalidateToken(token);
        SecurityContextHolder.clearContext();
        log.info("User logged out successfully");
    }

    public UserDTO getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return mapToUserDTO(user);
    }

    @Transactional(readOnly = true)
    public List<UserDTO> getAdjusters() {
        return userRepository.findDistinctByRoles_Name(Role.RoleName.ROLE_ADJUSTER).stream()
                .map(this::mapToUserDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserDTO> getManagers() {
        return userRepository.findDistinctByRoles_Name(Role.RoleName.ROLE_MANAGER).stream()
                .map(this::mapToUserDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .sorted((left, right) -> {
                    LocalDateTime leftCreatedAt = left.getCreatedAt() == null ? LocalDateTime.MIN : left.getCreatedAt();
                    LocalDateTime rightCreatedAt = right.getCreatedAt() == null ? LocalDateTime.MIN : right.getCreatedAt();
                    return rightCreatedAt.compareTo(leftCreatedAt);
                })
                .map(this::mapToUserDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserDTO getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return mapToUserDTO(user);
    }

    private String generateToken(User user) {
        Set<String> roles = user.getRoles().stream()
                .map(r -> r.getName().name())
                .collect(Collectors.toSet());
        return jwtTokenProvider.generateToken(user.getId(), user.getUsername(), roles);
    }

    private void invalidatePendingChallenges(Long userId, LocalDateTime now) {
        List<LoginOtpChallenge> pendingChallenges = loginOtpChallengeRepository.findByUserIdAndConsumedFalse(userId);
        if (pendingChallenges.isEmpty()) {
            return;
        }
        for (LoginOtpChallenge pending : pendingChallenges) {
            pending.setConsumed(true);
            if (pending.getExpiresAt() == null || pending.getExpiresAt().isAfter(now)) {
                pending.setExpiresAt(now);
            }
        }
        loginOtpChallengeRepository.saveAll(pendingChallenges);
    }

    private String generateOtpCode() {
        int value = secureRandom.nextInt(1_000_000);
        return String.format(Locale.ROOT, "%06d", value);
    }

    private void sendLoginOtpEmail(User user, String otpCode, LocalDateTime expiresAt) {
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setTo(user.getEmail());
            String fromAddress = (otpMailFrom == null || otpMailFrom.isBlank()) ? null : otpMailFrom.trim();
            if (fromAddress != null) {
                mail.setFrom(fromAddress);
            }
            mail.setSubject("ClaimSwift Login OTP");
            mail.setText(buildOtpMailText(user, otpCode, expiresAt));
            mailSender.send(mail);
        } catch (Exception ex) {
            log.error("Failed to send login OTP email to user {}", user.getUsername(), ex);
            throw new IllegalArgumentException("Unable to send OTP email. Please try again.");
        }
    }

    private String buildOtpMailText(User user, String otpCode, LocalDateTime expiresAt) {
        String displayName = user.getFirstName() == null || user.getFirstName().isBlank()
                ? user.getUsername()
                : user.getFirstName().trim();
        return "Hello " + displayName + ",\n\n"
                + "Your ClaimSwift login OTP is: " + otpCode + "\n"
                + "This OTP expires at: " + expiresAt + "\n\n"
                + "If you did not request this login, please ignore this email.\n\n"
                + "ClaimSwift Team";
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            return "your registered email";
        }
        String[] parts = email.split("@", 2);
        String local = parts[0];
        String domain = parts[1];
        if (local.length() <= 2) {
            return "*@" + domain;
        }
        String maskedLocal = local.charAt(0) + "***" + local.charAt(local.length() - 1);
        return maskedLocal + "@" + domain;
    }

    private UserDTO mapToUserDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .roles(user.getRoles().stream()
                        .map(role -> role.getName().name())
                        .collect(Collectors.toSet()))
                .status(user.getStatus().name())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private Set<Role> resolveRoles(Set<String> roleNames) {
        return roleNames.stream()
                .map(this::parseRoleName)
                .map(roleName -> roleRepository.findByName(roleName)
                        .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleName)))
                .collect(Collectors.toSet());
    }

    private Role.RoleName parseRoleName(String roleName) {
        String normalized = roleName == null ? "" : roleName.trim().toUpperCase();
        if (!normalized.startsWith("ROLE_")) {
            normalized = "ROLE_" + normalized;
        }
        try {
            return Role.RoleName.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid role: " + roleName);
        }
    }

    private User.UserStatus resolveStatus(String status, User.UserStatus defaultStatus) {
        if (status == null || status.isBlank()) {
            return defaultStatus;
        }
        try {
            return User.UserStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid status: " + status);
        }
    }
}
