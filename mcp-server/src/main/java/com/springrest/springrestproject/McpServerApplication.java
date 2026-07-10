package com.springrest.springrestproject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.springrest.springrestproject.mcp.tools.McpTools;
import com.springrest.springrestproject.service.interfaces.ReadServices.*;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@org.springframework.context.annotation.ComponentScan(excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE, classes = {com.springrest.springrestproject.config.AdminInitializerConfig.class}))
public class McpServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }

    @Bean
    public McpTools mcpTools(IMetadataReadService metadataReadService, IDataReadService dataReadService, IRelationReadService relationReadService, IUserReadService userReadService) {
        return new McpTools(metadataReadService, dataReadService, relationReadService, userReadService);
    }
}
