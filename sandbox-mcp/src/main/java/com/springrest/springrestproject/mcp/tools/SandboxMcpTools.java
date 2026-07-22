package com.springrest.springrestproject.mcp.tools;

import com.springrest.scripting.engine.ScriptExecutionService;
import com.springrest.scripting.model.ScriptCaller;
import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.dto.response.scripting.ScriptExecutionResponse;
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
import com.springrest.springrestproject.service.interfaces.IDatabaseManagementService;
import com.springrest.springrestproject.service.interfaces.IDataService;
import com.springrest.springrestproject.service.interfaces.IMetadataService;
import com.springrest.springrestproject.service.interfaces.IPersonalAccessTokenService;
import com.springrest.springrestproject.service.interfaces.IRelationService;
import com.springrest.springrestproject.service.interfaces.IUserService;
import org.flywaydb.core.Flyway;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import javax.sql.DataSource;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * SandboxMcpTools provides a comprehensive set of operations for the Sandbox MCP Server.
 * Unlike the standard McpTools which is read-only, SandboxMcpTools includes both read
 * and write/mutation operations across data, metadata, relations, and user services.
 * This allows the agent full control to experiment and alter schema and data in a safe sandbox environment.
 */
public class SandboxMcpTools {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SandboxMcpTools.class);

    public record McpPageResult<T>(List<T> content, int totalPages, long totalElements, int size, int number) {
        public static <T> McpPageResult<T> of(Page<T> page) {
            return new McpPageResult<>(page.getContent(), page.getTotalPages(), page.getTotalElements(), page.getSize(), page.getNumber());
        }
    }

    private final IMetadataService metadataService;
    private final IDataService dataService;
    private final IRelationService relationService;
    private final IUserService userService;
    private final DataSource sandboxDataSource;
    private final IDatabaseManagementService databaseManagementService;
    private final IPersonalAccessTokenService patService;
    private final ScriptExecutionService scriptExecutionService;

    @Value("${mcp.pat:}")
    private String mcpPat;

    @Value("${LIVE_DB_URL:}")
    private String liveDbUrl;

    @Value("${LIVE_DB_USER:}")
    private String liveDbUser;

    @Value("${LIVE_DB_PASS:}")
    private String liveDbPass;

    @Value("${spring.datasource.url}")
    private String sandboxDbUrl;

    @Value("${spring.datasource.username}")
    private String sandboxDbUser;

    @Value("${spring.datasource.password}")
    private String sandboxDbPass;

    // Generic default system user id for MCP tasks.
    private static final Long MCP_SYSTEM_USER_ID = 2L;

    public SandboxMcpTools(IMetadataService metadataService, IDataService dataService,
                           IRelationService relationService, IUserService userService,
                           DataSource sandboxDataSource, IDatabaseManagementService databaseManagementService,
                           IPersonalAccessTokenService patService, ScriptExecutionService scriptExecutionService) {
        this.metadataService = metadataService;
        this.dataService = dataService;
        this.relationService = relationService;
        this.userService = userService;
        this.sandboxDataSource = sandboxDataSource;
        this.databaseManagementService = databaseManagementService;
        this.patService = patService;
        this.scriptExecutionService = scriptExecutionService;
    }

    private Long resolveActiveUserId(boolean requireWrite) {
        if (mcpPat == null || mcpPat.trim().isEmpty()) {
            if (requireWrite) {
                throw new ApplicationException(ErrorCode.UNAUTHORIZED_ACCESS, "Personal Access Token (PAT) is required for write/CRUD operations.");
            }
            return MCP_SYSTEM_USER_ID; // Fallback to guest read-only agent ID
        }
        return patService.validateTokenAndGetUserId(mcpPat);
    }

    // ==========================================
    // DATABASE MANAGEMENT SERVICES
    // ==========================================

    @McpTool(description = "Drop everything in the sandbox database and recreate the default schema using Flyway. Requires confirm='yes-reset-sandbox'")
    public String resetSandboxDatabaseToDefault(String confirm) {
        Long userId = resolveActiveUserId(true);
        return databaseManagementService.resetDatabaseToDefault(confirm, userId);
    }

    @McpTool(description = "Evict all cached table metadata and relations (e.g. after a direct DB write to sys_table_metadata/sys_column_metadata/sys_relation_metadata bypasses the normal cache-evicting write path). Requires ADMIN role and a valid PAT.")
    public String evictAllCache() {
        Long userId = resolveActiveUserId(true);
        return databaseManagementService.evictAllCache(userId);
    }

    @McpTool(description = "Copy the schema and data from the live database into the sandbox database. Requires confirm='yes-overwrite-sandbox'")
    public String copyLiveDatabaseToSandbox(String confirm) throws Exception {
        if (!"yes-overwrite-sandbox".equals(confirm)) {
            throw new IllegalArgumentException("Action aborted. You must pass exactly 'yes-overwrite-sandbox' to confirm.");
        }
        if (liveDbUrl == null || liveDbUrl.isBlank()) {
            throw new IllegalStateException("LIVE_DB_URL is not configured.");
        }

        String pgDumpUrl = liveDbUrl.startsWith("jdbc:") ? liveDbUrl.substring(5) : liveDbUrl;
        String psqlUrl = sandboxDbUrl.startsWith("jdbc:") ? sandboxDbUrl.substring(5) : sandboxDbUrl;

        File dumpFile = File.createTempFile("live_db_dump_" + UUID.randomUUID(), ".sql");
        try {
            // 1. Run pg_dump on live DB
            ProcessBuilder dumpPb = new ProcessBuilder(
                    "pg_dump", "--dbname=" + pgDumpUrl, "--file=" + dumpFile.getAbsolutePath(), "--no-owner", "--no-privileges"
            );
            dumpPb.environment().put("PGUSER", liveDbUser);
            dumpPb.environment().put("PGPASSWORD", liveDbPass);
            Process dumpProcess = dumpPb.start();
            int dumpExitCode = dumpProcess.waitFor();
            if (dumpExitCode != 0) {
                throw new RuntimeException("pg_dump failed with exit code " + dumpExitCode);
            }

            // 2. Clean sandbox DB using Flyway
            Flyway flyway = Flyway.configure()
                    .dataSource(sandboxDataSource)
                    .cleanDisabled(false)
                    .load();
            flyway.clean();

            // 3. Run psql to restore the dump to sandbox DB
            ProcessBuilder restorePb = new ProcessBuilder(
                    "psql", "--dbname=" + psqlUrl, "--file=" + dumpFile.getAbsolutePath()
            );
            restorePb.environment().put("PGUSER", sandboxDbUser);
            restorePb.environment().put("PGPASSWORD", sandboxDbPass);
            Process restoreProcess = restorePb.start();
            int restoreExitCode = restoreProcess.waitFor();
            if (restoreExitCode != 0) {
                throw new RuntimeException("psql failed with exit code " + restoreExitCode);
            }

            return "Live database successfully copied to sandbox.";
        } finally {
            if (dumpFile.exists() && !dumpFile.delete()) {
                log.warn("Failed to delete temp dump file: {}", dumpFile.getAbsolutePath());
                dumpFile.deleteOnExit();
            }
        }
    }

    // ==========================================
    // METADATA SERVICES (Read + Write)
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

    @McpTool(description = "Create a new table in the database. Note: Strict schema requires dummy values for columnContext, id, tableName, isAuditEnabled, and validationRegex must be EMAIL or PHONE or null.")
    public TableMetadata createTable(String tableName, TableCreateRequest request) {
        return metadataService.createTable(tableName, request, MCP_SYSTEM_USER_ID);
    }

    @McpTool(description = "Delete a table by its name")
    public TableResponse deleteTableByName(String tableName) {
        return metadataService.deleteTableByName(tableName, MCP_SYSTEM_USER_ID);
    }

    @McpTool(description = "Log a schema change manually")
    public void logSchemaChange(String tableName, String sql) {
        metadataService.logSchemaChange(tableName, sql, MCP_SYSTEM_USER_ID);
    }

    // ==========================================
    // DATA SERVICES (Read + Write)
    // ==========================================

    @McpTool(description = "Execute a complex select query on a table. Note: Schema requires passing dummy values for audit, conditions, fields, page, relations, size, sorts inside the request. You can fetch nested related tables by specifying them in the 'relations' map.")
    public QueryResponse executeSelect(QueryRequest request, int page, int size) {
        return dataService.executeSelect(request, MCP_SYSTEM_USER_ID, PageRequest.of(page, size));
    }

    @McpTool(description = "Get paginated raw data from a specific table")
    public McpPageResult<Map<String, Object>> getTableData(String tableName, Boolean showSensitive, int page, int size) {
        return McpPageResult.of(dataService.getTableData(tableName, showSensitive != null ? showSensitive : false, PageRequest.of(page, size), MCP_SYSTEM_USER_ID));
    }

    @McpTool(description = "Find a row in a table by its ID")
    public Map<String, Object> findRowById(String tableName, Long id) {
        return dataService.findRowById(tableName, id, false, MCP_SYSTEM_USER_ID);
    }

    @McpTool(description = "Insert a new row into a table. Note: Data map must be passed in 'rowData'. You can also perform nested insertions by including a 'relations' map within 'rowData'.")
    public DataResponse insertRow(TableInsertRequest request) {
        return dataService.insertRow(request, MCP_SYSTEM_USER_ID);
    }

    @McpTool(description = "Update an existing row in a table by ID. Note: Nested updates via the 'relations' map are NOT supported; passing them will cause an error.")
    public DataResponse updateRowById(String tableName, Long id, Map<String, Object> updateData) {
        return dataService.updateRowById(tableName, id, updateData, MCP_SYSTEM_USER_ID);
    }

    @McpTool(description = "Delete a row in a table by ID")
    public DataResponse deleteRowById(String tableName, Long id) {
        return dataService.deleteRowById(tableName, id, MCP_SYSTEM_USER_ID);
    }

    // ==========================================
    // RELATION SERVICES (Read + Write)
    // ==========================================

    @McpTool(description = "Get all relations across the database")
    public List<RelationResponse> getAllRelations() {
        return relationService.getAllRelations();
    }

    @McpTool(description = "Get all resolved relations for a specific table")
    public List<ResolvedRelation> getRelationsForTable(String tableName) {
        return relationService.getRelationsForTable(tableName);
    }

    @McpTool(description = "Create a One-to-One relation between two tables")
    public RelationResponse createOneToOneRelation(DirectRelationRequest request) {
        return relationService.createOneToOneRelation(request, MCP_SYSTEM_USER_ID);
    }

    @McpTool(description = "Create a Many-to-One relation between two tables")
    public RelationResponse createManyToOneRelation(DirectRelationRequest request) {
        return relationService.createManyToOneRelation(request, MCP_SYSTEM_USER_ID);
    }

    @McpTool(description = "Create a Many-to-Many relation between two tables")
    public RelationResponse createManyToManyRelation(ManyToManyRelationRequest request) {
        return relationService.createManyToManyRelation(request, MCP_SYSTEM_USER_ID);
    }

    @McpTool(description = "Insert many-to-many relation data by relation ID")
    public ManyToManyInsertRequest insertManyToManyDataById(Long relationId, ManyToManyInsertRequest request) {
        return relationService.insertManyToManyDataById(relationId, request);
    }

    @McpTool(description = "Insert many-to-many relation data by table name. Note: The tableName must be the name of the generated junction table.")
    public ManyToManyInsertRequest insertManyToManyDataByName(String tableName, ManyToManyInsertRequest request) {
        return relationService.insertManyToManyDataByName(tableName, request);
    }

    @McpTool(description = "Delete many-to-many relation data by relation ID")
    public void deleteManyToManyDataById(Long relationId, ManyToManyInsertRequest request) {
        relationService.deleteManyToManyDataById(relationId, request);
    }

    @McpTool(description = "Delete many-to-many relation data by table name. Note: The tableName must be the name of the generated junction table.")
    public void deleteManyToManyDataByName(String tableName, ManyToManyInsertRequest request) {
        relationService.deleteManyToManyDataByName(tableName, request);
    }

    // ==========================================
    // USER SERVICES (Read + Write)
    // ==========================================

    @McpTool(description = "Get a paginated list of all users. Note: page must be >= 0.")
    public McpPageResult<UserResponse> getAllUsers(int page, int size) {
        return McpPageResult.of(userService.getAllUsers(PageRequest.of(page, size)));
    }

    @McpTool(description = "Get a user by their internal ID")
    public UserRequest getUserById(Long id) {
        return userService.getUserById(id);
    }

    @McpTool(description = "Get a user by their exact name")
    public UserRequest getUserByName(String name) {
        return userService.getUserByName(name);
    }

    @McpTool(description = "Find a user entity by their username")
    public AppUser findByUsername(String username) {
        AppUser user = userService.findByUsername(username);
        return AppUser.builder().id(user.id()).username(user.username()).active(user.active()).build();
    }

    @McpTool(description = "Create a new user. Note: Strict schema requires passing a dummy 'id' (e.g. 0), which will be auto-generated.")
    public UserRequest createUser(AppUser user) {
        return userService.createUser(user, MCP_SYSTEM_USER_ID);
    }

    @McpTool(description = "Delete a user by their internal ID")
    public void deleteUserById(Long id) {
        userService.deleteUserById(id, MCP_SYSTEM_USER_ID);
    }

    // ==========================================
    // SCRIPTING SERVICES
    // ==========================================

    @McpTool(description = "Execute a JavaScript script against the sandbox database on the caller's behalf. Requires the SCRIPT_ENGINEER group and a valid PAT. Chrome DevTools debugging is not available over MCP (it requires a human attaching a browser) - use POST /api/script with debug_enabled=true for that instead.")
    public ScriptExecutionResponse executeScript(String script) {
        Long userId = resolveActiveUserId(true);
        ScriptCaller caller = new ScriptCaller(String.valueOf(userId), Set.of("MCP"));
        return scriptExecutionService.execute(script, caller, false);
    }
}
