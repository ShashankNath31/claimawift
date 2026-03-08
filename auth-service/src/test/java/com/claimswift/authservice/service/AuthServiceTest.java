package com.claimswift.authservice.service;

import com.claimswift.authservice.dto.AdminCreateUserRequest;
import com.claimswift.authservice.dto.AuthResponse;
import com.claimswift.authservice.dto.LoginChallengeResponse;
import com.claimswift.authservice.dto.LoginRequest;
import com.claimswift.authservice.dto.RegisterRequest;
import com.claimswift.authservice.dto.UserDTO;
import com.claimswift.authservice.dto.VerifyLoginOtpRequest;
import com.claimswift.authservice.entity.LoginOtpChallenge;
import com.claimswift.authservice.entity.Role;
import com.claimswift.authservice.entity.User;
import com.claimswift.authservice.repository.LoginOtpChallengeRepository;
import com.claimswift.authservice.repository.RoleRepository;
import com.claimswift.authservice.repository.UserRepository;
import com.claimswift.authservice.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    private Role policyholderRole;
    private Role managerRole;
    private User user;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest();
        registerRequest.setUsername("testuser");
        registerRequest.setEmail("test@example.com");
        registerRequest.setPassword("password123");
        registerRequest.setFirstName("Test");
        registerRequest.setLastName("User");
        registerRequest.setPhoneNumber("9999999999");

        loginRequest = new LoginRequest();
        loginRequest.setUsernameOrEmail("testuser");
        loginRequest.setPassword("password123");

        verifyLoginOtpRequest = new VerifyLoginOtpRequest();
        verifyLoginOtpRequest.setChallengeId("challenge-1");
        verifyLoginOtpRequest.setOtp("123456");

        policyholderRole = Role.builder().id(1L).name(Role.RoleName.ROLE_POLICYHOLDER).build();
        managerRole = Role.builder().id(2L).name(Role.RoleName.ROLE_MANAGER).build();

        user = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .password("encodedPassword")
                .firstName("Test")
                .lastName("User")
                .phoneNumber("9999999999")
                .roles(Set.of(policyholderRole))
                .status(User.UserStatus.ACTIVE)
                .build();

        ReflectionTestUtils.setField(authService, "twoFactorEnabled", true);
        ReflectionTestUtils.setField(authService, "otpExpiryMinutes", 5L);
        ReflectionTestUtils.setField(authService, "otpMaxAttempts", 3);
        ReflectionTestUtils.setField(authService, "otpMailFrom", "noreply@claimswift.com");
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registerSuccess() {
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(roleRepository.findByName(Role.RoleName.ROLE_POLICYHOLDER)).thenReturn(Optional.of(policyholderRole));
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(jwtTokenProvider.generateToken(any(), anyString(), any())).thenReturn("token");
        when(jwtTokenProvider.getExpirationTime()).thenReturn(86400000L);

        AuthResponse response = authService.register(registerRequest);

        assertNotNull(response);
        assertEquals("Bearer", response.getTokenType());
        assertEquals("token", response.getAccessToken());
    }

    @Test
    void registerThrowsWhenUsernameExists() {
        when(userRepository.existsByUsername(anyString())).thenReturn(true);
        assertThrows(RuntimeException.class, () -> authService.register(registerRequest));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registerThrowsWhenEmailExists() {
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(true);
        assertThrows(RuntimeException.class, () -> authService.register(registerRequest));
    }

    @Test
    void createInternalUserSuccess() {
        AdminCreateUserRequest request = new AdminCreateUserRequest();
        request.setUsername("manager");
        request.setEmail("manager@test.com");
        request.setPassword("password123");
        request.setFirstName("Manager");
        request.setLastName("One");
        request.setRoles(Set.of("manager"));
        request.setStatus("active");

        User created = User.builder()
                .id(9L)
                .username("manager")
                .email("manager@test.com")
                .password("encoded")
                .roles(Set.of(managerRole))
                .status(User.UserStatus.ACTIVE)
                .build();

        when(userRepository.existsByUsername("manager")).thenReturn(false);
        when(userRepository.existsByEmail("manager@test.com")).thenReturn(false);
        when(roleRepository.findByName(Role.RoleName.ROLE_MANAGER)).thenReturn(Optional.of(managerRole));
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenReturn(created);

        UserDTO dto = authService.createInternalUser(request, "admin");

        assertEquals("manager", dto.getUsername());
        assertTrue(dto.getRoles().contains("ROLE_MANAGER"));
        assertEquals("ACTIVE", dto.getStatus());
    }

    @Test
    void updateUserRolesAndStatus() {
        User existing = User.builder()
                .id(3L)
                .username("ops")
                .email("ops@test.com")
                .password("enc")
                .roles(Set.of(policyholderRole))
                .status(User.UserStatus.ACTIVE)
                .build();

        when(userRepository.findById(3L)).thenReturn(Optional.of(existing));
        when(roleRepository.findByName(Role.RoleName.ROLE_MANAGER)).thenReturn(Optional.of(managerRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserDTO roleUpdated = authService.updateUserRoles(3L, Set.of("manager"), "admin");
        assertTrue(roleUpdated.getRoles().contains("ROLE_MANAGER"));

        UserDTO statusUpdated = authService.updateUserStatus(3L, "suspended", "admin");
        assertEquals("SUSPENDED", statusUpdated.getStatus());
    }

    @Test
    void loginSuccessCreatesChallengeAndSendsEmail() {
        Authentication authentication = new UsernamePasswordAuthenticationToken("testuser", "password123");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(userRepository.findByUsernameOrEmail("testuser", "testuser")).thenReturn(Optional.of(user));
        when(loginOtpChallengeRepository.findByUserIdAndConsumedFalse(user.getId())).thenReturn(List.of());
        when(passwordEncoder.encode(anyString())).thenReturn("encodedOtp");
        when(loginOtpChallengeRepository.save(any(LoginOtpChallenge.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        LoginChallengeResponse response = authService.login(loginRequest);

        assertNotNull(response.getChallengeId());
        assertTrue(response.getExpiresInSeconds() > 0);
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void loginInvalidatesPendingChallenges() {
        Authentication authentication = new UsernamePasswordAuthenticationToken("testuser", "password123");
        LoginOtpChallenge pending = LoginOtpChallenge.builder()
                .id(7L)
                .challengeId("old")
                .user(user)
                .otpHash("h")
                .attemptCount(0)
                .consumed(false)
                .expiresAt(LocalDateTime.now().plusMinutes(2))
                .build();

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(userRepository.findByUsernameOrEmail("testuser", "testuser")).thenReturn(Optional.of(user));
        when(loginOtpChallengeRepository.findByUserIdAndConsumedFalse(user.getId())).thenReturn(List.of(pending));
        when(passwordEncoder.encode(anyString())).thenReturn("encodedOtp");
        when(loginOtpChallengeRepository.save(any(LoginOtpChallenge.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        authService.login(loginRequest);

        verify(loginOtpChallengeRepository).saveAll(any(List.class));
    }

    @Test
    void loginThrowsWhenTwoFactorDisabled() {
        ReflectionTestUtils.setField(authService, "twoFactorEnabled", false);

        Authentication authentication = new UsernamePasswordAuthenticationToken("testuser", "password123");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(userRepository.findByUsernameOrEmail("testuser", "testuser")).thenReturn(Optional.of(user));

        assertThrows(IllegalArgumentException.class, () -> authService.login(loginRequest));
    }

    @Test
    void verifyLoginOtpSuccess() {
        LoginOtpChallenge challenge = LoginOtpChallenge.builder()
                .challengeId("challenge-1")
                .user(user)
                .otpHash("encodedOtp")
                .attemptCount(0)
                .consumed(false)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();

        when(loginOtpChallengeRepository.findByChallengeId("challenge-1")).thenReturn(Optional.of(challenge));
        when(passwordEncoder.matches("123456", "encodedOtp")).thenReturn(true);
        when(jwtTokenProvider.generateToken(any(), anyString(), any())).thenReturn("mock-token");
        when(jwtTokenProvider.getExpirationTime()).thenReturn(86400000L);
        when(loginOtpChallengeRepository.save(any(LoginOtpChallenge.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any(User.class))).thenReturn(user);

        AuthResponse response = authService.verifyLoginOtp(verifyLoginOtpRequest);

        assertEquals("mock-token", response.getAccessToken());
        assertEquals("Bearer", response.getTokenType());
    }

    @Test
    void verifyLoginOtpHandlesExpiredChallenge() {
        LoginOtpChallenge challenge = LoginOtpChallenge.builder()
                .challengeId("challenge-1")
                .user(user)
                .otpHash("encodedOtp")
                .attemptCount(0)
                .consumed(false)
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();

        when(loginOtpChallengeRepository.findByChallengeId("challenge-1")).thenReturn(Optional.of(challenge));
        when(loginOtpChallengeRepository.save(any(LoginOtpChallenge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThrows(IllegalArgumentException.class, () -> authService.verifyLoginOtp(verifyLoginOtpRequest));
    }

    @Test
    void verifyLoginOtpIncrementsAttemptsOnInvalidOtp() {
        LoginOtpChallenge challenge = LoginOtpChallenge.builder()
                .challengeId("challenge-1")
                .user(user)
                .otpHash("encodedOtp")
                .attemptCount(1)
                .consumed(false)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();

        when(loginOtpChallengeRepository.findByChallengeId("challenge-1")).thenReturn(Optional.of(challenge));
        when(passwordEncoder.matches("123456", "encodedOtp")).thenReturn(false);
        when(loginOtpChallengeRepository.save(any(LoginOtpChallenge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThrows(IllegalArgumentException.class, () -> authService.verifyLoginOtp(verifyLoginOtpRequest));

        ArgumentCaptor<LoginOtpChallenge> captor = ArgumentCaptor.forClass(LoginOtpChallenge.class);
        verify(loginOtpChallengeRepository).save(captor.capture());
        assertEquals(2, captor.getValue().getAttemptCount());
    }

    @Test
    void verifyLoginOtpStopsWhenMaxAttemptsExceeded() {
        LoginOtpChallenge challenge = LoginOtpChallenge.builder()
                .challengeId("challenge-1")
                .user(user)
                .otpHash("encodedOtp")
                .attemptCount(3)
                .consumed(false)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();

        when(loginOtpChallengeRepository.findByChallengeId("challenge-1")).thenReturn(Optional.of(challenge));
        when(loginOtpChallengeRepository.save(any(LoginOtpChallenge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThrows(IllegalArgumentException.class, () -> authService.verifyLoginOtp(verifyLoginOtpRequest));
    }

    @Test
    void refreshTokenAndLogout() {
        when(jwtTokenProvider.validateToken("old")).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken("old")).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateToken(any(), anyString(), any())).thenReturn("new-token");
        when(jwtTokenProvider.getExpirationTime()).thenReturn(1000L);

        AuthResponse refreshed = authService.refreshToken("old");
        assertEquals("new-token", refreshed.getAccessToken());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("testuser", "n/a")
        );
        authService.logout("new-token");
        verify(jwtTokenProvider).invalidateToken("new-token");
        assertEquals(null, SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void refreshTokenThrowsForInvalidToken() {
        when(jwtTokenProvider.validateToken("bad")).thenReturn(false);
        assertThrows(RuntimeException.class, () -> authService.refreshToken("bad"));
    }

    @Test
    void userReadMethodsMapData() {
        User adjuster = User.builder()
                .id(2L)
                .username("adj")
                .email("adj@test.com")
                .password("x")
                .roles(Set.of(Role.builder().name(Role.RoleName.ROLE_ADJUSTER).build()))
                .status(User.UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now().minusDays(1))
                .build();
        User manager = User.builder()
                .id(3L)
                .username("mgr")
                .email("mgr@test.com")
                .password("x")
                .roles(Set.of(managerRole))
                .status(User.UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        when(userRepository.findDistinctByRoles_Name(Role.RoleName.ROLE_ADJUSTER)).thenReturn(List.of(adjuster));
        when(userRepository.findDistinctByRoles_Name(Role.RoleName.ROLE_MANAGER)).thenReturn(List.of(manager));
        when(userRepository.findAll()).thenReturn(List.of(adjuster, manager));

        UserDTO current = authService.getCurrentUser(1L);
        List<UserDTO> adjusters = authService.getAdjusters();
        List<UserDTO> managers = authService.getManagers();
        List<UserDTO> users = authService.getAllUsers();
        UserDTO byId = authService.getUserById(1L);

        assertEquals("testuser", current.getUsername());
        assertEquals(1, adjusters.size());
        assertEquals(1, managers.size());
        assertEquals(2, users.size());
        assertEquals("mgr", users.get(0).getUsername());
        assertEquals("testuser", byId.getUsername());
        assertThrows(RuntimeException.class, () -> authService.getCurrentUser(99L));
    }
}
