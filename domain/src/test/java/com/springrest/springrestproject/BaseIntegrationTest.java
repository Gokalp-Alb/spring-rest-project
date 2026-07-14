package com.springrest.springrestproject;

import com.redis.testcontainers.RedisContainer;
import org.flywaydb.core.Flyway;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SuppressWarnings("resource")
public abstract class BaseIntegrationTest {

    protected static final PostgreSQLContainer<?> postgresContainer;
    protected static final RedisContainer redisContainer;

    static {
        postgresContainer = new PostgreSQLContainer<>("postgres:15-alpine")
                .withDatabaseName("spring_rest_project_test_db")
                .withUsername("test_user")
                .withPassword("test_pass");
        postgresContainer.start();

        // Run migrations on the container database
        Flyway flyway = Flyway.configure()
                .dataSource(postgresContainer.getJdbcUrl(), postgresContainer.getUsername(), postgresContainer.getPassword())
                .placeholders(java.util.Map.of("mcp_password", "test_pword"))
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load();
        flyway.migrate();

        redisContainer = new RedisContainer("redis:7.2-alpine");
        redisContainer.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);

        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));

        // Mock properties required by core configs
        registry.add("app.jwt.secret", () -> "test-jwt-secret-key-12345");
        registry.add("app.admin.username", () -> "testadmin");
        registry.add("app.admin.password", () -> "testpass");
        registry.add("spring.flyway.placeholders.mcp_password", () -> "test_mcp_password");
    }
}
