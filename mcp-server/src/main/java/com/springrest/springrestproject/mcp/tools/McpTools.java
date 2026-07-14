package com.springrest.springrestproject.mcp.tools;

import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.dto.request.data.TableInsertRequest;
import com.springrest.springrestproject.dto.request.query.QueryRequest;
import com.springrest.springrestproject.dto.request.relation.DirectRelationRequest;
import com.springrest.springrestproject.dto.request.relation.ManyToManyInsertRequest;
import com.springrest.springrestproject.dto.request.relation.ManyToManyRelationRequest;
import com.springrest.springrestproject.dto.request.table.TableCreateRequest;
import com.springrest.springrestproject.dto.request.user.UserRequest;
import com.springrest.springrestproject.dto.response.data.DataResponse;
import com.springrest.springrestproject.dto.response.data.QueryResponse;
import com.springrest.springrestproject.dto.response.relation.RelationResponse;
import com.springrest.springrestproject.dto.response.relation.ResolvedRelation;
import com.springrest.springrestproject.dto.response.table.TableResponse;
import com.springrest.springrestproject.dto.response.user.UserResponse;
import com.springrest.springrestproject.model.table.TableMetadata;
import com.springrest.springrestproject.model.user.AppUser;
import com.springrest.springrestproject.model.user.Role;
import com.springrest.springrestproject.service.interfaces.IDatabaseManagementService;
import com.springrest.springrestproject.service.interfaces.IDataService;
import com.springrest.springrestproject.service.interfaces.IMetadataService;
import com.springrest.springrestproject.service.interfaces.IPersonalAccessTokenService;
import com.springrest.springrestproject.service.interfaces.IRelationService;
import com.springrest.springrestproject.service.interfaces.IUserService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.beans.factory.annotation.Value;
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

    private final IMetadataService metadataService;
    private final IDataService dataService;
    private final IRelationService relationService;
    private final IUserService userService;
    private final IPersonalAccessTokenService patService;
    private final IDatabaseManagementService databaseManagementService;

    @Value("${mcp.pat:}")
    private String mcpPat;

    public McpTools(IMetadataService metadataService, IDataService dataService,
                    IRelationService relationService, IUserService userService,
                    IPersonalAccessTokenService patService, IDatabaseManagementService databaseManagementService) {
        this.metadataService = metadataService;
        this.dataService = dataService;
        this.relationService = relationService;
        this.userService = userService;
        this.patService = patService;
        this.databaseManagementService = databaseManagementService;
    }

    private Long resolveActiveUserId(boolean requireWrite) {
        if (mcpPat == null || mcpPat.trim().isEmpty()) {
            if (requireWrite) {
                throw new ApplicationException(ErrorCode.UNAUTHORIZED_ACCESS, "Personal Access Token (PAT) is required for write/CRUD operations.");
            }
            return 2L; // Fallback to guest read-only agent ID
        }
        return patService.validateTokenAndGetUserId(mcpPat);
    }

    private void verifyAdminRole(Long userId) {
        UserRequest executor = userService.getUserById(userId);
        if (executor.role() != Role.ADMIN) {
            throw new ApplicationException(ErrorCode.UNAUTHORIZED_ACCESS, "Only ADMIN users are authorized to perform this operation.");
        }
    }

    // ==========================================
    // DATABASE MANAGEMENT SERVICES
    // ==========================================

    @McpTool(description = "Drop everything in the database and recreate the default schema using Flyway. Requires confirm='yes-reset-sandbox'. Requires ADMIN role and a valid PAT.")
    public String resetSandboxDatabaseToDefault(String confirm) {
        Long userId = resolveActiveUserId(true);
        return databaseManagementService.resetDatabaseToDefault(confirm, userId);
    }

    // ==========================================
    // METADATA READ & WRITE SERVICES
    // ==========================================

    @McpTool(description = "Get a paginated list of all tables in the database. Note: page must be >= 0.")
    public McpPageResult<TableResponse> getAllTables(int page, int size) {
        return McpPageResult.of(metadataService.getAllTables(PageRequest.of(page, size)));
    }

    @McpTool(description = "Get a table by its internal ID")
    public TableResponse getTableById(Long tableId) {
        return metadataService.getTableById(tableId);
    }

    @McpTool(description = "Get a table by its name")
    public TableResponse getTableByName(String tableName) {
        return metadataService.getTableByName(tableName);
    }

    @McpTool(description = "Get the schema for a specific table")
    public Map<String, Object> generateSchemaForTable(String tableName) {
        return metadataService.generateSchemaForTable(tableName);
    }

    @McpTool(description = "Create a new table in the database. Note: Strict schema requires dummy values for columnContext, id, tableName, isAuditEnabled, and validationRegex must be EMAIL or PHONE or null. Requires a valid PAT.")
    public TableMetadata createTable(String tableName, TableCreateRequest request) {
        Long userId = resolveActiveUserId(true);
        return metadataService.createTable(tableName, request, userId);
    }

    @McpTool(description = "Delete a table by its name. Requires a valid PAT.")
    public TableResponse deleteTableByName(String tableName) {
        Long userId = resolveActiveUserId(true);
        return metadataService.deleteTableByName(tableName, userId);
    }

    @McpTool(description = "Log a schema change manually. Requires a valid PAT.")
    public void logSchemaChange(String tableName, String sql) {
        Long userId = resolveActiveUserId(true);
        metadataService.logSchemaChange(tableName, sql, userId);
    }

    // ==========================================
    // DATA READ & WRITE SERVICES
    // ==========================================

    @McpTool(description = "Execute a complex select query on a table. Note: Schema requires passing dummy values for audit, conditions, fields, page, relations, size, sorts inside the request. You can fetch nested related tables by specifying them in the 'relations' map.")
    public QueryResponse executeSelect(QueryRequest request, int page, int size) {
        Long userId = resolveActiveUserId(false);
        return dataService.executeSelect(request, userId, PageRequest.of(page, size));
    }

    @McpTool(description = "Get paginated raw data from a specific table")
    public McpPageResult<Map<String, Object>> getTableData(String tableName, Boolean showSensitive, int page, int size) {
        Long userId = resolveActiveUserId(false);
        return McpPageResult.of(dataService.getTableData(tableName, showSensitive != null ? showSensitive : false, PageRequest.of(page, size), userId));
    }

    @McpTool(description = "Find a row in a table by its ID")
    public Map<String, Object> findRowById(String tableName, Long id) {
        Long userId = resolveActiveUserId(false);
        return dataService.findRowById(tableName, id, false, userId);
    }

    @McpTool(description = "Insert a new row into a table. Note: Data map must be passed in 'rowData'. You can also perform nested insertions by including a 'relations' map within 'rowData'. Requires a valid PAT.")
    public DataResponse insertRow(String tableName, Map<String, Object> rowData) {
        Long userId = resolveActiveUserId(true);
        return dataService.insertRow(new TableInsertRequest(tableName, rowData), userId);
    }

    @McpTool(description = "Delete a row in a table by ID. Requires a valid PAT.")
    public DataResponse deleteRowById(String tableName, Long id) {
        Long userId = resolveActiveUserId(true);
        return dataService.deleteRowById(tableName, id, userId);
    }

    @McpTool(description = "Update an existing row in a table by ID. Note: Nested updates via the 'relations' map are NOT supported; passing them will cause an error. Requires a valid PAT.")
    public DataResponse updateRowById(String tableName, Long id, Map<String, Object> updateData) {
        Long userId = resolveActiveUserId(true);
        return dataService.updateRowById(tableName, id, updateData, userId);
    }

    // ==========================================
    // RELATION READ & WRITE SERVICES
    // ==========================================

    @McpTool(description = "Get all relations across the database")
    public List<RelationResponse> getAllRelations() {
        return relationService.getAllRelations();
    }

    @McpTool(description = "Get all resolved relations for a specific table")
    public List<ResolvedRelation> getRelationsForTable(String tableName) {
        return relationService.getRelationsForTable(tableName);
    }

    @McpTool(description = "Create a One-to-One relation between two tables. Requires a valid PAT.")
    public RelationResponse createOneToOneRelation(DirectRelationRequest request) {
        Long userId = resolveActiveUserId(true);
        return relationService.createOneToOneRelation(request, userId);
    }

    @McpTool(description = "Create a Many-to-One relation between two tables. Requires a valid PAT.")
    public RelationResponse createManyToOneRelation(DirectRelationRequest request) {
        Long userId = resolveActiveUserId(true);
        return relationService.createManyToOneRelation(request, userId);
    }

    @McpTool(description = "Create a Many-to-Many relation between two tables. Requires a valid PAT.")
    public RelationResponse createManyToManyRelation(ManyToManyRelationRequest request) {
        Long userId = resolveActiveUserId(true);
        return relationService.createManyToManyRelation(request, userId);
    }

    @McpTool(description = "Insert many-to-many relation data by relation ID. Requires a valid PAT.")
    public ManyToManyInsertRequest insertManyToManyDataById(Long relationId, ManyToManyInsertRequest request) {
        resolveActiveUserId(true);
        return relationService.insertManyToManyDataById(relationId, request);
    }

    @McpTool(description = "Insert many-to-many relation data by table name. Note: The tableName must be the name of the generated junction table. Requires a valid PAT.")
    public ManyToManyInsertRequest insertManyToManyDataByName(String tableName, ManyToManyInsertRequest request) {
        resolveActiveUserId(true);
        return relationService.insertManyToManyDataByName(tableName, request);
    }

    @McpTool(description = "Delete many-to-many relation data by relation ID. Requires a valid PAT.")
    public void deleteManyToManyDataById(Long relationId, ManyToManyInsertRequest request) {
        resolveActiveUserId(true);
        relationService.deleteManyToManyDataById(relationId, request);
    }

    @McpTool(description = "Delete many-to-many relation data by table name. Note: The tableName must be the name of the generated junction table. Requires a valid PAT.")
    public void deleteManyToManyDataByName(String tableName, ManyToManyInsertRequest request) {
        resolveActiveUserId(true);
        relationService.deleteManyToManyDataByName(tableName, request);
    }

    // ==========================================
    // USER READ & WRITE SERVICES
    // ==========================================

    @McpTool(description = "Get a paginated list of all users. Note: page must be >= 0.")
    public McpPageResult<UserResponse> getAllUsers(int page, int size) {
        Long userId = resolveActiveUserId(true);
        verifyAdminRole(userId);
        return McpPageResult.of(userService.getAllUsers(PageRequest.of(page, size)));
    }

    @McpTool(description = "Get a user by their internal ID")
    public UserRequest getUserById(Long id) {
        Long userId = resolveActiveUserId(true);
        verifyAdminRole(userId);
        return userService.getUserById(id);
    }

    @McpTool(description = "Get a user by their exact name")
    public UserRequest getUserByName(String name) {
        Long userId = resolveActiveUserId(true);
        verifyAdminRole(userId);
        return userService.getUserByName(name);
    }

    @McpTool(description = "Find a user entity by their username")
    public AppUser findByUsername(String username) {
        Long userId = resolveActiveUserId(true);
        verifyAdminRole(userId);
        return userService.findByUsername(username);
    }

    @McpTool(description = "Create a new user. Note: Strict schema requires passing a dummy 'id' (e.g. 0), which will be auto-generated. Requires ADMIN role and a valid PAT.")
    public UserRequest createUser(AppUser user) {
        Long userId = resolveActiveUserId(true);
        verifyAdminRole(userId);
        return userService.createUser(user, userId);
    }

    @McpTool(description = "Delete a user by their internal ID. Requires ADMIN role and a valid PAT.")
    public void deleteUserById(Long id) {
        Long userId = resolveActiveUserId(true);
        verifyAdminRole(userId);
        userService.deleteUserById(id, userId);
    }
}
