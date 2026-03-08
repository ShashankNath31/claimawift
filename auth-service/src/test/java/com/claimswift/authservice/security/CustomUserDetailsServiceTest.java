package com.claimswift.authservice.security;

import com.claimswift.authservice.entity.Role;
import com.claimswift.authservice.entity.User;
import com.claimswift.authservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void loadUserByUsernameReturnsUserDetailsForActiveUser() {
        Role role = Role.builder().name(Role.RoleName.ROLE_MANAGER).build();
        User user = User.builder()
                .username("manager")
                .password("encoded")
                .status(User.UserStatus.ACTIVE)
                .roles(Set.of(role))
                .build();

        when(userRepository.findByUsernameOrEmail("manager", "manager")).thenReturn(Optional.of(user));

        UserDetails details = customUserDetailsService.loadUserByUsername("manager");

        assertEquals("manager", details.getUsername());
        assertEquals(1, details.getAuthorities().size());
    }

    @Test
    void loadUserByUsernameThrowsForInactiveUser() {
        User user = User.builder()
                .username("inactive")
                .password("encoded")
                .status(User.UserStatus.SUSPENDED)
                .roles(Set.of(Role.builder().name(Role.RoleName.ROLE_POLICYHOLDER).build()))
                .build();
        when(userRepository.findByUsernameOrEmail("inactive", "inactive")).thenReturn(Optional.of(user));

        assertThrows(UsernameNotFoundException.class, () -> customUserDetailsService.loadUserByUsername("inactive"));
    }

    @Test
    void loadUserByUsernameThrowsWhenUserDoesNotExist() {
        when(userRepository.findByUsernameOrEmail("missing", "missing")).thenReturn(Optional.empty());
        assertThrows(UsernameNotFoundException.class, () -> customUserDetailsService.loadUserByUsername("missing"));
    }
}
