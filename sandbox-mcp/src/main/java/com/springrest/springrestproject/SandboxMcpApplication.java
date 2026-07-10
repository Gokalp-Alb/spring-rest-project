package com.springrest.springrestproject;

import com.springrest.springrestproject.config.AdminInitializerConfig;
import com.springrest.springrestproject.mcp.tools.SandboxMcpTools;
import com.springrest.springrestproject.service.interfaces.IDataService;
import com.springrest.springrestproject.service.interfaces.IMetadataService;
import com.springrest.springrestproject.service.interfaces.IRelationService;
import com.springrest.springrestproject.service.interfaces.IUserService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

import javax.sql.DataSource;

import static org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE;

@SpringBootApplication
@ComponentScan(excludeFilters = @ComponentScan.Filter(type = ASSIGNABLE_TYPE, classes = {AdminInitializerConfig.class}))
public class SandboxMcpApplication {
    public static void main(String[] args) {
        SpringApplication.run(SandboxMcpApplication.class, args);
    }

    @Bean
    public SandboxMcpTools sandboxMcpTools(IMetadataService metadataService, IDataService dataService, IRelationService relationService, IUserService userService, DataSource dataSource) {
        return new SandboxMcpTools(metadataService, dataService, relationService, userService, dataSource);
    }
}
