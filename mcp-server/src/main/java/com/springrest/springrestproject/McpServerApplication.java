package com.springrest.springrestproject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.springrest.springrestproject.mcp.tools.McpTools;
import com.springrest.springrestproject.service.interfaces.IDataService;
import com.springrest.springrestproject.service.interfaces.IMetadataService;
import com.springrest.springrestproject.service.interfaces.IRelationService;
import com.springrest.springrestproject.service.interfaces.IUserService;
import com.springrest.springrestproject.service.interfaces.IPersonalAccessTokenService;
import com.springrest.springrestproject.service.interfaces.IDatabaseManagementService;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@org.springframework.context.annotation.ComponentScan(excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE, classes = {com.springrest.springrestproject.config.AdminInitializerConfig.class}))
public class McpServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }

    @Bean
    public McpTools mcpTools(
            IMetadataService metadataService,
            IDataService dataService,
            IRelationService relationService,
            IUserService userService,
            IPersonalAccessTokenService patService,
            IDatabaseManagementService databaseManagementService) {
        return new McpTools(metadataService, dataService, relationService, userService, patService, databaseManagementService);
    }
}
