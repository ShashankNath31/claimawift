package com.claimswift.authservice.service;

import com.claimswift.authservice.dto.AuthResponse;
import com.claimswift.authservice.dto.LoginChallengeResponse;
import com.claimswift.authservice.dto.LoginRequest;
import com.claimswift.authservice.dto.RegisterRequest;
import com.claimswift.authservice.dto.VerifyLoginOtpRequest;
import com.claimswift.authservice.entity.LoginOtpChallenge;
import com.claimswift.authservice.entity.Role;
import com.claimswift.authservice.entity.User;
import com.claimswift.authservice.repository.LoginOtpChallengeRepository;
import com.claimswift.authservice.repository.RoleRepository;
import com.claimswift.authservice.repository.UserRepository;
import com.claimswift.authservice.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private LoginOtpChallengeRepository loginOtpChallengeRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private VerifyLoginOtpRequest verifyLoginOtpRequest;
    private User user;
    private Role role;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest();
        registerRequest.setUsername("testuser");
        registerRequest.setEmail("test@example.com");
        registerRequest.setPassword("password123");
        registerRequest.setFirstName("Test");
        registerRequest.setLastName("User");

        loginRequest = new LoginRequest();
        loginRequest.setUsernameOrEmail("testuser");
        loginRequest.setPassword("password123");

        verifyLoginOtpRequest = new VerifyLoginOtpRequest();
        verifyLoginOtpRequest.setChallengeId("challenge-1");
        verifyLoginOtpRequest.setOtp("123456");

        role = new Role();
        role.setId(1L);
        role.setName(Role.RoleName.ROLE_POLICYHOLDER);

        user = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .password("encodedPassword")
                .firstName("Test")
                .lastName("User")
                .roles(Set.of(role))
                .status(User.UserStatus.ACTIVE)
                .build();
    }

    @Test
    void register_Success() {
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(roleRepository.findByName(any())).thenReturn(Optional.of(role));
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(jwtTokenProvider.getExpirationTime()).thenReturn(86400000L);

        AuthResponse response = authService.register(registerRequest);

        assertNotNull(response);
        assertEquals("Bearer", response.getTokenType());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_UsernameExists_ThrowsException() {
        when(userRepository.existsByUsername(anyString())).thenReturn(true);

        assertThrows(RuntimeException.class, () -> authService.register(registerRequest));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_Success() {
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(userRepository.findByUsernameOrEmail(anyString(), anyString())).thenReturn(Optional.of(user));
        when(loginOtpChallengeRepository.findByUserIdAndConsumedFalse(any())).thenReturn(java.util.List.of());
        when(passwordEncoder.encode(anyString())).thenReturn("encodedOtp");
        when(loginOtpChallengeRepository.save(any(LoginOtpChallenge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LoginChallengeResponse response = authService.login(loginRequest);

        assertNotNull(response);
        assertNotNull(response.getChallengeId());
        assertNotNull(response.getMaskedEmail());
    }

    @Test
    void login_InvalidCredentials_ThrowsException() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new RuntimeException("Invalid credentials"));

        assertThrows(RuntimeException.class, () -> authService.login(loginRequest));
    }

    @Test
    void verifyLoginOtp_Success() {
        LoginOtpChallenge challenge = LoginOtpChallenge.builder()
                .challengeId("challenge-1")
                .user(user)
                .otpHash("encodedOtp")
                .attemptCount(0)
                .consumed(false)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();

        when(loginOtpChallengeRepository.findByChallengeId("challenge-1")).thenReturn(Optional.of(challenge));
        when(passwordEncoder.matches(eq("123456"), eq("encodedOtp"))).thenReturn(true);
        when(jwtTokenProvider.generateToken(any(), anyString(), any())).thenReturn("mock-token");
        when(jwtTokenProvider.getExpirationTime()).thenReturn(86400000L);
        when(loginOtpChallengeRepository.save(any(LoginOtpChallenge.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any(User.class))).thenReturn(user);

        AuthResponse response = authService.verifyLoginOtp(verifyLoginOtpRequest);

        assertNotNull(response);
        assertNotNull(response.getAccessToken());
        assertEquals("Bearer", response.getTokenType());
    }
}
