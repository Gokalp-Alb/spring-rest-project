package com.springrest.springrestproject;

import com.springrest.scripting.engine.ScriptExecutionService;
import com.springrest.springrestproject.config.AdminInitializerConfig;
import com.springrest.springrestproject.mcp.tools.SandboxMcpTools;
import com.springrest.springrestproject.service.interfaces.IDataService;
import com.springrest.springrestproject.service.interfaces.IMetadataService;
import com.springrest.springrestproject.service.interfaces.IRelationService;
import com.springrest.springrestproject.service.interfaces.IUserService;
import com.springrest.springrestproject.service.interfaces.IDatabaseManagementService;
import com.springrest.springrestproject.service.interfaces.IPersonalAccessTokenService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

import javax.sql.DataSource;

import static org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE;

@SpringBootApplication(scanBasePackages = "com.springrest")
@ConfigurationPropertiesScan(basePackages = "com.springrest")
@ComponentScan(basePackages = "com.springrest", excludeFilters = @ComponentScan.Filter(type = ASSIGNABLE_TYPE, classes = {AdminInitializerConfig.class}))
public class SandboxMcpApplication {
    public static void main(String[] args) {
        SpringApplication.run(SandboxMcpApplication.class, args);
    }

    @Bean
    public SandboxMcpTools sandboxMcpTools(IMetadataService metadataService, IDataService dataService,
            IRelationService relationService, IUserService userService, DataSource dataSource,
            IDatabaseManagementService databaseManagementService, IPersonalAccessTokenService patService,
            ScriptExecutionService scriptExecutionService) {
        return new SandboxMcpTools(metadataService, dataService, relationService, userService, dataSource,
                databaseManagementService, patService, scriptExecutionService);
    }
}
