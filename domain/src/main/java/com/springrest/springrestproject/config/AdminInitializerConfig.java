package com.springrest.springrestproject.config;

import com.springrest.springrestproject.model.user.AppUser;
import com.springrest.springrestproject.model.user.GroupName;
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

    private static final Long SYSTEM_ACTOR_ID = 0L;

    private final AppUserRepo userRepo;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username}")
    private String adminUsername;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Bean
    public CommandLineRunner initializeDefaultAdmin() {
        return args -> {
            if (!userRepo.existsByUsername(adminUsername)) {
                AppUser defaultAdmin = AppUser.builder()
                        .username(adminUsername)
                        .password(passwordEncoder.encode(adminPassword))
                        .active(true)
                        .build();
                AppUser saved = userRepo.saveInternal(defaultAdmin, true, SYSTEM_ACTOR_ID);
                userRepo.saveGroupInternal(saved.id(), GroupName.ADMIN, SYSTEM_ACTOR_ID, true);
                userRepo.saveGroupInternal(saved.id(), GroupName.REGISTERED_USER, SYSTEM_ACTOR_ID, true);
                userRepo.saveGroupInternal(saved.id(), GroupName.DATABASE_ADMIN, SYSTEM_ACTOR_ID, true);
            }
        };
    }
}
