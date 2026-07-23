package com.springrest.springrestproject.service.implementations;

import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.dto.request.user.UserRequest;
import com.springrest.springrestproject.model.user.GroupName;
import com.springrest.springrestproject.service.implementations.redis.RelationCacheService;
import com.springrest.springrestproject.service.implementations.redis.TableMetadataCacheService;
import com.springrest.springrestproject.service.interfaces.IDatabaseManagementService;
import com.springrest.springrestproject.service.interfaces.IUserService;
import lombok.RequiredArgsConstructor;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DatabaseManagementService implements IDatabaseManagementService {

    private final IUserService userService;
    private final DataSource dataSource;
    private final TableMetadataCacheService tableMetadataCacheService;
    private final RelationCacheService relationCacheService;

    @Value("${spring.flyway.locations:classpath:db/migration}")
    private String flywayLocations;

    @Value("${spring.flyway.placeholders.mcp_password:mcp_pword_123}")
    private String mcpPassword;

    @Override
    public String resetDatabaseToDefault(String confirm, Long userId) {
        if (!"yes-reset-db".equals(confirm)) {
            throw new IllegalArgumentException("Action aborted. You must pass exactly 'yes-reset-db' to confirm.");
        }

        UserRequest executor = userService.getUserById(userId);
        if (!executor.groups().contains(GroupName.ADMIN)) {
            throw new ApplicationException(ErrorCode.UNAUTHORIZED_ACCESS, "Only ADMIN users are authorized to perform this operation.");
        }

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(flywayLocations)
                .cleanDisabled(false)
                .placeholders(Map.of("mcp_password", mcpPassword))
                .load();

        flyway.clean();
        flyway.migrate();
        return "Database successfully reset to default Flyway state.";
    }

    @Override
    public String evictAllCache(Long userId) {
        UserRequest executor = userService.getUserById(userId);
        if (!executor.groups().contains(GroupName.ADMIN)) {
            throw new ApplicationException(ErrorCode.UNAUTHORIZED_ACCESS, "Only ADMIN users are authorized to perform this operation.");
        }

        tableMetadataCacheService.evictAll();
        relationCacheService.evictAll();
        return "All cached table metadata and relations evicted.";
    }
}
