package com.springrest.springrestproject.config;

import com.springrest.springrestproject.model.AppUser;
import com.springrest.springrestproject.model.Role;
import com.springrest.springrestproject.repository.IUserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
public class AdminInitializerConfig {

    private final IUserRepo userRepo;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username}")
    private String adminUsername;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Bean
    public CommandLineRunner initializeDefaultAdmin() {
        return args -> {
            boolean adminExists = userRepo.findAll().stream()
                    .anyMatch(user -> user.getRole() == Role.ADMIN);
            if (!adminExists) {
                AppUser defaultAdmin = new AppUser();
                defaultAdmin.setUsername(adminUsername);
                defaultAdmin.setPassword(passwordEncoder.encode(adminPassword));
                defaultAdmin.setRole(Role.ADMIN);
                userRepo.save(defaultAdmin);
            }
        };
    }
}