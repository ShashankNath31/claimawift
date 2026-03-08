package com.claimswift.authservice.config;

import com.claimswift.authservice.entity.Role;
import com.claimswift.authservice.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoleDataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        ensureRoleEnumIncludesAdmin();

        Map<Role.RoleName, String> roleDefinitions = new LinkedHashMap<>();
        roleDefinitions.put(Role.RoleName.ROLE_ADMIN, "Admin can manage users, roles, and all system operations");
        roleDefinitions.put(Role.RoleName.ROLE_POLICYHOLDER, "Policyholder can submit and track own claims");
        roleDefinitions.put(Role.RoleName.ROLE_ADJUSTER, "Adjuster can review claims and process decisions");
        roleDefinitions.put(Role.RoleName.ROLE_MANAGER, "Manager can assign claims and access reports");

        roleDefinitions.forEach(this::createRoleIfMissing);
    }

    private void createRoleIfMissing(Role.RoleName roleName, String description) {
        if (roleRepository.existsByName(roleName)) {
            return;
        }

        Role role = Role.builder()
                .name(roleName)
                .description(description)
                .build();
        roleRepository.save(role);
        log.info("Seeded role: {}", roleName);
    }

    private void ensureRoleEnumIncludesAdmin() {
        try {
            String sql = """
                    SELECT COLUMN_TYPE
                    FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'roles'
                      AND COLUMN_NAME = 'name'
                    """;
            String columnType = jdbcTemplate.queryForObject(sql, String.class);

            if (columnType == null || !columnType.toLowerCase().startsWith("enum(")) {
                return;
            }

            if (columnType.contains("'ROLE_ADMIN'")) {
                return;
            }

            jdbcTemplate.execute("""
                    ALTER TABLE roles
                    MODIFY COLUMN name ENUM('ROLE_ADMIN','ROLE_POLICYHOLDER','ROLE_ADJUSTER','ROLE_MANAGER') NOT NULL
                    """);
            log.info("Updated roles.name enum to include ROLE_ADMIN");
        } catch (Exception ex) {
            log.warn("Could not auto-migrate roles.name enum: {}", ex.getMessage());
        }
    }
}
