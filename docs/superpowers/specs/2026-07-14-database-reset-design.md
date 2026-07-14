# Database Reset and MCP Agent Capability Design

## Purpose
Expose a system endpoint to reset the database to a baseline state. This functionality is currently buried inside MCP tools. It should be extracted into a reusable domain service and exposed via a REST endpoint for admin users. In addition, the Flyway schema will be updated to consolidate the `mcp_agent_readonly` permissions into a single unified `mcp_agent` with broader capabilities.

## Requirements & Constraints
- Extract the database reset logic from `McpTools` and `SandboxMcpTools` to a `DatabaseManagementService`.
- Create a new REST API controller `SystemController` with a `POST /api/system/reset-database` endpoint.
- Only users with the `ADMIN` role should be able to trigger this reset.
- Combine the Flyway migration file `V2__Mcp_Write_Permissions.sql` into `V1__Baseline_Schema.sql`. Delete V2.
- Rename the `mcp_agent_readonly` role to `mcp_agent`, and update its password placeholder to `${mcp_password}`. Remove mentions of it being "read-only".
- The implementation must adhere to existing project patterns (e.g. error handling, response wrapping, annotations).

## Architecture & Data Flow

### 1. Database & Flyway Schema Changes
- **V1__Baseline_Schema.sql**: 
  - Merge the contents of `V2__Mcp_Write_Permissions.sql` into this file.
  - Rename the `mcp_agent_readonly` role to `mcp_agent`. 
  - Update the password placeholder from `${mcp_readonly_password}` to `${mcp_password}`. 
  - Remove all comments mentioning "read-only".
- **V2__Mcp_Write_Permissions.sql**: 
  - Delete this file entirely (the user will manually reset the local database).
- **Properties/Config**: 
  - Update `application.properties` placeholder from `mcp_readonly_password` to `mcp_password`.

### 2. Service Layer (Domain)
- **IDatabaseManagementService / DatabaseManagementService**: 
  - Implementation must follow existing service patterns (e.g., standard logging, error handling with `ApplicationException`, `@Service` and `@RequiredArgsConstructor`).
  - Implement a `resetDatabaseToDefault(String confirm, Long userId)` method.
  - Verify that the `confirm` parameter matches `"yes-reset-sandbox"`.
  - Inject `IUserService` and verify the `userId` corresponds to a user with the `ADMIN` role. Throw `ApplicationException(ErrorCode.UNAUTHORIZED_ACCESS, ...)` if not.
  - Move the Flyway logic (and its dependencies: `DataSource`, `@Value` for `flywayLocations`, `@Value` for `mcpPassword`) from the MCP tools into this service.

### 3. API Layer
- **SystemController**: 
  - Create a new `@RestController` mapped to `/api/system`.
  - Add a `POST /reset-database` endpoint.
  - Require the `confirm` string as a `@RequestParam`. 
  - Extract the `userId` from the incoming JWT token via `@AuthenticationPrincipal Jwt jwt` and `jwt.getClaim("userId")`.
  - Delegate the reset logic to `IDatabaseManagementService`.
  - Return a standard `ApiResponse<String>`.

### 4. MCP Tools Updates
- **McpTools.java & SandboxMcpTools.java**:
  - Remove the Flyway bean configuration and associated `@Value` fields.
  - Inject `IDatabaseManagementService` into the constructors.
  - Update `resetSandboxDatabaseToDefault(String confirm)` to simply call `databaseManagementService.resetDatabaseToDefault(confirm, resolveActiveUserId(true))` and return its result.
