package com.springrest.springrestproject.mcp.tools;

import com.springrest.springrestproject.dto.request.query.QueryRequest;
import com.springrest.springrestproject.dto.request.user.UserRequest;
import com.springrest.springrestproject.dto.response.data.QueryResponse;
import com.springrest.springrestproject.dto.response.relation.RelationResponse;
import com.springrest.springrestproject.dto.response.relation.ResolvedRelation;
import com.springrest.springrestproject.dto.response.table.TableResponse;
import com.springrest.springrestproject.dto.response.user.UserResponse;
import com.springrest.springrestproject.model.user.AppUser;
import com.springrest.springrestproject.service.interfaces.ReadServices.IDataReadService;
import com.springrest.springrestproject.service.interfaces.ReadServices.IMetadataReadService;
import com.springrest.springrestproject.service.interfaces.ReadServices.IRelationReadService;
import com.springrest.springrestproject.service.interfaces.ReadServices.IUserReadService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;

public class McpTools {

    public record McpPageResult<T>(List<T> content, int totalPages, long totalElements, int size, int number) {
        public static <T> McpPageResult<T> of(Page<T> page) {
            return new McpPageResult<>(page.getContent(), page.getTotalPages(), page.getTotalElements(), page.getSize(), page.getNumber());
        }
    }

    private final IMetadataReadService metadataReadService;
    private final IDataReadService dataReadService;
    private final IRelationReadService relationReadService;
    private final IUserReadService userReadService;

    // Generic default system user id for MCP tasks.
    private static final Long MCP_SYSTEM_USER_ID = 2L;

    public McpTools(IMetadataReadService metadataReadService, IDataReadService dataReadService,
                    IRelationReadService relationReadService, IUserReadService userReadService) {
        this.metadataReadService = metadataReadService;
        this.dataReadService = dataReadService;
        this.relationReadService = relationReadService;
        this.userReadService = userReadService;
    }

    // ==========================================
    // METADATA READ SERVICES
    // ==========================================

    @McpTool(description = "Get a paginated list of all tables in the database")
    public McpPageResult<TableResponse> getAllTables(int page, int size) {
        return McpPageResult.of(metadataReadService.getAllTables(PageRequest.of(page, size)));
    }

    @McpTool(description = "Get a table by its internal ID")
    public TableResponse getTableById(Long tableId) {
        return metadataReadService.getTableById(tableId);
    }

    @McpTool(description = "Get a table by its name")
    public TableResponse getTableByName(String tableName) {
        return metadataReadService.getTableByName(tableName);
    }

    @McpTool(description = "Get the schema for a specific table")
    public Map<String, Object> generateSchemaForTable(String tableName) {
        return metadataReadService.generateSchemaForTable(tableName);
    }

    // ==========================================
    // DATA READ SERVICES
    // ==========================================

    @McpTool(description = "Execute a complex select query on a table")
    public QueryResponse executeSelect(QueryRequest request, int page, int size) {
        return dataReadService.executeSelect(request, MCP_SYSTEM_USER_ID, PageRequest.of(page, size));
    }

    @McpTool(description = "Get paginated raw data from a specific table")
    public McpPageResult<Map<String, Object>> getTableData(String tableName, Boolean showSensitive, int page, int size) {
        return McpPageResult.of(dataReadService.getTableData(tableName, showSensitive != null ? showSensitive : false, PageRequest.of(page, size), MCP_SYSTEM_USER_ID));
    }

    @McpTool(description = "Find a row in a table by its ID")
    public Map<String, Object> findRowById(String tableName, Long id) {
        return dataReadService.findRowById(tableName, id, false, MCP_SYSTEM_USER_ID);
    }

    // ==========================================
    // RELATION READ SERVICES
    // ==========================================

    @McpTool(description = "Get all relations across the database")
    public List<RelationResponse> getAllRelations() {
        return relationReadService.getAllRelations();
    }

    @McpTool(description = "Get all resolved relations for a specific table")
    public List<ResolvedRelation> getRelationsForTable(String tableName) {
        return relationReadService.getRelationsForTable(tableName);
    }

    // ==========================================
    // USER READ SERVICES
    // ==========================================

    @McpTool(description = "Get a paginated list of all users")
    public McpPageResult<UserResponse> getAllUsers(int page, int size) {
        return McpPageResult.of(userReadService.getAllUsers(PageRequest.of(page, size)));
    }

    @McpTool(description = "Get a user by their internal ID")
    public UserRequest getUserById(Long id) {
        return userReadService.getUserById(id);
    }

    @McpTool(description = "Get a user by their exact name")
    public UserRequest getUserByName(String name) {
        return userReadService.getUserByName(name);
    }

    @McpTool(description = "Find a user entity by their username")
    public AppUser findByUsername(String username) {
        return userReadService.findByUsername(username);
    }
}
