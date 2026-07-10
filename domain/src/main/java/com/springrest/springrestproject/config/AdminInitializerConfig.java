package com.springrest.springrestproject.config;

import com.springrest.springrestproject.model.user.AppUser;
import com.springrest.springrestproject.model.user.Role;
import com.springrest.springrestproject.repository.AppUserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
public class AdminInitializerConfig {

    private final AppUserRepo userRepo;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username}")
    private String adminUsername;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Bean
    public CommandLineRunner initializeDefaultAdmin() {
        return args -> {
            boolean adminExists = userRepo.existsByRole(Role.ADMIN);
            if (!adminExists) {
                AppUser defaultAdmin = AppUser.builder()
                        .username(adminUsername)
                        .password(passwordEncoder.encode(adminPassword))
                        .role(Role.ADMIN)
                        .active(true)
                        .build();
                userRepo.save(defaultAdmin);
            }
        };
    }
}