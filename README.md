# Dynamic Schema Engine and Virtual Runtime Storage

A highly flexible, metadata-driven Spring Boot platform designed to bypass compile-time database boundaries. By storing database schemas as structural metadata and generating SQL dynamically at runtime, this system allows clients to create tables, alter structures, create complex relational models, and execute CRUD operations instantly through JSON API endpoints without requiring code recompilation or application reboots.

---

## Architecture Overview

The workspace is organized as a multi-module Maven project separating the core domain logic, web controllers, and Model Context Protocol (MCP) server adapters.

* **domain**: The core engine module. Contains the schema registry services, caching configurations (Redis), database migrations (Flyway), query parser helper classes, group-based governance guards, and transaction layers. Also defines the `ScriptHookInvoker`/`ScriptHookSession` interfaces consumed by `DataService` and the Kafka publisher/processor, without depending on `scripting` itself.
* **api**: The HTTP entry point module. Exposes standard REST endpoints for authentication, user/group management, schema mutations, dynamic data manipulations, event integration, script execution, and system administration.
* **scripting**: Houses the GraalVM Polyglot (JavaScript) execution engine. Powers both ad-hoc script execution (`POST /api/script`) and persisted hook scripts (`sys_scripts`) that fire on database writes and Kafka publish/consume events, implementing the SPI defined in `domain`.
* **mcp-server**: A stdio-based Model Context Protocol (MCP) server operating against the live database. Exposes read tools for schema/data/relations/users alongside PAT-gated write and administrative tools (table/data mutations, script execution and management, Kafka mapping management, database reset).
* **sandbox-mcp**: A stdio-based write-capable MCP server. Allows AI agents to interact with a dedicated sandbox database to test migrations, dynamic table creation, and insertions in isolation, mirroring the same tool surface as `mcp-server`.

---

## Core Technologies and Libraries

* **Backend Framework**: Java 21 and Spring Boot 4.1.0. Preserves method parameters at compilation to facilitate runtime reflection.
* **Database & Persistence**: PostgreSQL, Spring JDBC (JdbcTemplate) for dynamic runtime queries, and JOOQ (Java Object-Oriented Querying) for system catalog queries. *This project runs purely on JOOQ and raw SQL template compiles without using JPA or Hibernate.*
* **Database Migration**: Flyway manages foundational system metadata and user tables.
* **Caching Layer**: Spring Data Redis configured with a custom **Jackson 3 (`tools.jackson`)** ObjectMapper for high-performance cache-aside caching.
* **Message Broker & Event Streaming**: Spring Kafka utilizing customized concurrent listener containers to hot-swap background consumer worker threads dynamically at runtime.
* **Scripting Runtime**: GraalVM Polyglot (`org.graalvm.polyglot`) sandboxed JavaScript execution contexts, powering both ad-hoc scripts and persisted hook scripts, with a watchdog-based execution timeout, optional Chrome DevTools Inspector debugging, and a Redis-backed namespaced cache API exposed to hook scripts.
* **Security & Auth**: Spring Security utilizing JSON Web Tokens (JWT) through OAuth2 Resource Server boundaries, with group-based (`sys_user_groups`) role authorization.
* **Integration Testing (Docker & Testcontainers)**: Driven by **Testcontainers** to instantiate isolated, disposable infrastructure. It interacts with a local Docker daemon to automatically pull, run, and tear down short-lived PostgreSQL, Redis, and Kafka broker containers.

---

## Core System Features

### 1. Metadata-Driven Registry and Constraint Auditing
Instead of mapping compile-time Java classes directly to physical structures, layout states are maintained in registry catalog tables (`sys_table_metadata` and `sys_column_metadata`).
* **Uniqueness Constraints**: The engine respects unique indicators (`isUnique`) on column definitions to maintain integrity across custom partitions.
* **Regex Pattern Validation**: Incoming payloads are checked against registered regex patterns (such as `EMAIL` or `PHONE` derived from the `ValidRegexPatterns` enum) at the boundary layer before executing database operations.

### 2. Supported Dynamic Data Types
Since data schemas are generated at runtime, the platform dynamically parses database column types. Any standard PostgreSQL DDL data type can be registered in the metadata catalog:
* **Textual**: `VARCHAR(length)`, `TEXT`, `CHAR(length)`
* **Numeric**: `INTEGER`, `BIGINT`, `SMALLINT`, `DOUBLE PRECISION`, `NUMERIC(precision, scale)`
* **Temporal**: `TIMESTAMP`, `DATE`, `TIME`
* **Boolean**: `BOOLEAN`
* **Structured**: `JSONB`, `JSON`, `UUID`

### 3. Dynamic Relational Mapping Engine and Smart Naming
As a dynamic table project, the platform treats table relationships as mutable metadata configurations. Relationships are wired at runtime via dynamic DDL commands rather than static code imports:
* **Direct Relations**: Programmatically appends foreign key column references directly into PostgreSQL table definitions to bind `One-to-One` and `Many-to-One` models.
* **Junction Tables**: For `Many-to-Many` relationships, the engine generates an intermediate junction table (identifiable by ending in `_jt`) containing foreign key mappings pointing to both parent tables.
* **Metadata Resolution**: Automatically constructs `ResolvedRelation` wrappers to dynamically map base-to-target links during CRUD projections.
* **Smart & Disambiguated Naming**: 
  * **Fallback Naming**: If a single relationship exists between two dynamic tables, the engine names the relation simply after the target table name (e.g. `t_employee` queries `t_department` simply using `"t_department"`).
  * **Disambiguated Naming**: If multiple foreign keys point between the same two tables (e.g. `home_department_id` and `work_department_id` on `t_employee`), naming them simply after the target table would cause conflicts. The resolver automatically detects this and generates descriptive, conflict-free selectors:
    * *Forward Join*: `{targetTable}_via_{sourceColumn}` (e.g. `t_department_via_home_department_id`).
    * *Reverse Join*: `{sourceTable}_via_{sourceColumn}` (e.g. `t_employee_via_home_department_id`).
    * *Many-to-Many Join*: `{targetTable}_via_{junctionTable}` (e.g. `t_employee_via_t_employee_t_department_jt`).

### 4. Parametric SQL Compiler & SQL Identifier Validation
The core data service converts JSON query configurations into native PostgreSQL commands on the fly. To prevent SQL Injection vulnerabilities, the platform implements a dual-layered security model:
* **Parametric Binding**: All user-provided data value parameters are executed via `JdbcTemplate` utilizing positional placeholders (`?`), enabling the database optimizer to cache query execution plans safely.
* **SQL Identifier Validation**: Since structural elements (such as table names, column names, sorting directives, filter targets, and relation map keys) cannot be parameterized, the platform routes them through `SqlIdentifierValidator`. This validator screens all dynamic identifiers against the strict regex pattern `^[\p{L}_][\p{L}\p{N}_]*$`. This allows international Unicode letters, numbers, and underscores, while blocking space characters, quotes, hyphens (`-`), or comments (`--`) to neutralize identifier-based injection pathways.
* **Recursive Query Tree Auditing**: The select query compiler recursively walks nested `QueryRequest` hierarchies to inspect and validate table references, projected field names, filtering columns, sorting columns, and nested relation alias keys at the boundary.

### 5. Dynamic DDL & Mutation Auditing (`sys_ddl_log`)
To record how dynamic database spaces mutate, mutations write a transaction entry.
* **Schema Log**: DDL operations parse executions via `rebuildFullSql` to reconstruct the exact executed query string, recording the statement along with the performing user's context in the `sys_ddl_log` repository.
* **Data Log**: For audited tables (`isAuditEnabled=true`), mutations (inserts, updates, deletes) automatically duplicate execution paths to record shadow historical records in corresponding target tables ending in `_log`.

### 6. Dynamic Query & Audit Query Engine
The SELECT engine supports standard data retrieval (`POST /api/queries/select`) with operators (such as `BETWEEN`, `BEFORE`, `AFTER`, `IN`, `LIKE`, etc.), sort directives, and column projection fields.
* **Audit Logs Querying**: If a table has auditing enabled (`isAuditEnabled`), clients can include an `audit` parameter block inside their select query. The compiler will map this select query against the shadow audit log table (ending in `_log`) rather than the active table, enabling clients to search for historic mutations, executed operation types (e.g. `POST`, `PUT`, `DELETE`), modified timeframes, or executing user IDs.

### 7. Masking and Privacy Obfuscation
Fields flagged as sensitive in the column metadata registry are intercepted during projection translation. If a non-privileged query request arrives, the generator rewrites the SQL projection from a standard selector to an on-the-fly masked representation (e.g. `'********' AS sensitive_field`), ensuring values are redacted before leaving the database layer.

### 8. Dynamic Event Synchronization
The platform includes an event synchronization interface mapping Kafka topics to database tables. Topics are modeled as first-class entities (`sys_kafka_topics`, looked up by name and auto-created if absent) rather than raw strings, and `sys_kafka_table_mappings` binds a table to a topic and direction (`INBOUND`/`OUTBOUND`) via `topic_id`. It detects configuration changes in the background and spins up or terminates consumer worker threads dynamically without disrupting the main application container.

### 9. Loop Prevention Guardrail
Updates processed by background synchronization workers run under a distinct system context (`userId = 0L`). The outbound transaction publisher checks this identifier and drops matching events, preventing infinite state-propagation loops between connected systems.

### 10. Group-Based Access Control & Restricted System Tables
User authorization is modeled as group membership (`sys_user_groups`) rather than a single role field. Recognized groups (`GroupName`): `ADMIN`, `REGISTERED_USER`, `SCRIPT_ENGINEER`, `KAFKA_ENGINEER`, `MCP_AGENT`, `DATABASE_ADMIN`. Endpoint access is enforced per-group in `SecurityConfig` (e.g. `SCRIPT_ENGINEER` for `/api/script/**`, `KAFKA_ENGINEER` for `/api/kafka-mappings/**`), and `sys_`-prefixed system tables are excluded from the generic dynamic-table CRUD path entirely, each backed instead by its own hand-written repository with an unconditional shadow `_log` write on every mutation.

### 11. Script Execution Engine
A GraalVM Polyglot JavaScript engine executes ad-hoc scripts submitted via `POST /api/script`, sandboxed with `console` (log capture) and `tables` (read-only `select` access) globals, a configurable execution timeout, and an unenforced memory-limit hint (GraalVM's open-source Polyglot API exposes no heap cap). Ad-hoc execution requires the `SCRIPT_ENGINEER` group and is entirely disabled when the `production` Spring profile is active (`SCRIPT_EXECUTION_DISABLED`). Optional Chrome DevTools Inspector debugging (`debug_enabled=true`) is available on port `4242`, guarded by a single-session lock so only one debug context can run at a time per process.

### 12. Persisted Script Hooks
Scripts can also be persisted against a table or Kafka topic (`sys_scripts`, managed via `/api/system-scripts`) and fire automatically rather than being invoked ad-hoc: `beforeSaveToDB`/`afterSaveToDB` around `DataService` inserts and updates, and `onOutboundTopic`/`onInboundTopic` around Kafka publish/consume. Hook scripts run inside the same transaction as the write that triggered them, so a thrown error inside a hook rolls back the write (and, for outbound hooks, the Kafka publish) that triggered it. Hook execution is never gated by the `production` profile, since hooks aren't an operator-reachable ad-hoc surface.

### 13. Namespaced Script Cache
Hook scripts (not ad-hoc scripts) additionally receive a `cache` global backed by Redis (`cache.get(key)`, `cache.set(key, value, ttlSeconds?)`, `cache.delete(key)`), automatically namespaced per script (`script:table:{tableId}:` or `script:topic:{topicId}:`) so cache keys never collide across scripts.

---

## Advanced Data Mechanics

### 1. Nested Insertions
The platform supports hierarchically inserting graphs of related tables in a single JSON payload. The engine parses the nested `"relations"` field and executes insertions based on the relation type:

* **Forward Relations (Many-to-One / One-to-One)**: If a child object represents a forward relation, the engine validates if the child has an `id`. If it does, the foreign key column is populated with that value. If it does not, the child is recursively inserted first, and the newly generated ID is captured and mapped into the parent's foreign key column prior to the parent's insertion.
* **Reverse Relations (One-to-Many)**: The parent record is inserted first to obtain its auto-generated primary key ID. The engine then iterates over the child collection: new records are recursively inserted with their foreign key pointing to the parent ID, and existing records (holding an `id`) are updated to link to the parent.

#### Sample JSON Payload (Nested Insertion - `POST /api/data/insert`)
```json
{
  "tableName": "t_department",
  "rowData": {
    "name": "Research and Development",
    "budget": 250000.00,
    "relations": {
      "t_manager_via_dept_id": [
        {
          "name": "Jane Doe",
          "email": "jane.doe@company.com"
        }
      ],
      "t_employee_via_dept_id": [
        {
          "id": 14
        },
        {
          "name": "John Smith",
          "email": "john.smith@company.com"
        }
      ]
    }
  }
}
```

### 2. Nested Querying
The select query engine parses recursive relations trees inside the `QueryRequest`. It performs optimized dynamic queries to fetch and join child structures:

* **Recursive Resolution**: The engine pulls the base data blocks, extracts their primary keys, and recursively runs bulk queries using SQL `IN` conditions to fetch and group related structures in a single batch.
* **Junction Table Fetching**: For Many-to-Many associations, it dynamically queries the intermediate `_jt` table map relations cleanly.
* **Relational Pagination**: Using `ROW_NUMBER() OVER (PARTITION BY ... ORDER BY ...)` window functions, the engine can paginate child collections independently (e.g., fetching a page of departments, with only the first 5 employees per department).

#### Sample JSON Payload (Nested Querying - `POST /api/queries/select`)
```json
{
  "tableName": "t_department",
  "fields": ["id", "name", "budget"],
  "conditions": [
    {
      "field": "budget",
      "operator": "GREATER_THAN",
      "value": 100000
    }
  ],
  "sorts": [
    {
      "field": "name",
      "direction": "ASC"
    }
  ],
  "relations": {
    "t_employee_via_dept_id": {
      "fields": ["name", "email"],
      "page": 0,
      "size": 5,
      "relations": {
        "t_computer_via_employee_id": {
          "fields": ["serial_number", "model"]
        }
      }
    }
  }
}
```

### 3. Graceful Cache Degradation (Optional Redis)
The application utilizes a custom `@PersistenceCache` annotation driven by an Aspect (`PersistenceCacheAspect`). SpEL expressions (e.g. `#tableName`) resolve cache keys dynamically based on request parameters.
* **Optional Caching**: The caching system implements a strict **Cache Aside** pattern. Cache read and write methods wrap Redis operations in try-catch blocks targeting `RedisConnectionFailureException`.
* **Database Fallback**: If a Redis server is unreachable, connection failures degrade gracefully to log warnings rather than throwing runtime exceptions, enabling the application to bypass the cache and run directly against PostgreSQL.

---

## API Documentation

### Authentication & Health
* `POST /api/auth/login`: Authenticates user credentials and returns a JWT access token.
* `GET /actuator/health`: System health status indicators check (PostgreSQL and Redis connection states).

### User Management (`/api/users/*`) (Admin access only)
* `POST /api/users`: Creates a new application user. Requires `AppUser` payload.
* `GET /api/users`: Returns a paginated list of registered users. Supports optional query parameters `page` (default `0`) and `pageSize` (default `10`).
* `GET /api/users/id/{id}`: Fetches user metadata details for the specified user ID.
* `GET /api/users/name/{name}`: Fetches user metadata details for the specified username.
* `DELETE /api/users/{id}`: Soft deletes and deactivates a user account.
* `POST /api/users/{userId}/groups`: Adds a group membership to a user. Requires a `GroupRequest` payload (`groupName`, one of `ADMIN`, `REGISTERED_USER`, `SCRIPT_ENGINEER`, `KAFKA_ENGINEER`, `MCP_AGENT`, `DATABASE_ADMIN`).
* `GET /api/users/{userId}/groups`: Lists all group memberships for a user.
* `DELETE /api/users/{userId}/groups/id/{groupId}`: Removes a group membership by its own ID.
* `DELETE /api/users/{userId}/groups/name/{groupName}`: Removes a group membership by group name.

### Schema Management (`/api/tables/*`)
* `POST /api/tables/{tableName}`: Dynamically builds a physical PostgreSQL table and registers its structural metadata. Requires a `TableCreateRequest` payload outlining columns, types, validation regex, and sensitivity configurations.
* `GET /api/tables`: Returns a paginated page of all registered table metadata. Supports parameters `page` and `pageSize`.
* `GET /api/tables/id/{tableId}`: Fetches table schema definitions using its system metadata database ID.
* `GET /api/tables/name/{tableName}`: Fetches table schema definitions using the table name.
* `GET /api/tables/jsonSchema/{tableName}`: Exposes the table schema layout as a standard JSON Schema model structure.
* `DELETE /api/tables/{tableName}`: Drops the physical database table layout and cascades registry cleanup to metadata registries and relational mappings.

### Relations Management (`/api/relations/*`)
* `GET /api/relations`: Fetches metadata for all established relationships across dynamic tables.
* `POST /api/relations/one-to-one`: Creates a 1:1 relation mapping between two tables. Requires a `DirectRelationRequest` specifying source and target tables, columns, and delete policies.
* `POST /api/relations/many-to-one`: Creates a N:1 relation mapping between two tables. Requires a `DirectRelationRequest`.
* `POST /api/relations/many-to-many`: Establishes a N:M mapping between two tables, dynamically creating an intermediate junction table (ending in `_jt`). Requires a `ManyToManyRelationRequest`.
* `POST /api/relations/many-to-many/id/{relationId}`: Inserts dynamic association records into a junction table by relation ID. Requires `ManyToManyInsertRequest`.
* `POST /api/relations/many-to-many/name/{tableName}`: Inserts dynamic association records into a junction table by junction table name. Requires `ManyToManyInsertRequest`.
* `DELETE /api/relations/many-to-many/id/{relationId}`: Removes association mappings from a junction table by relation ID. Requires `ManyToManyInsertRequest`.
* `DELETE /api/relations/many-to-many/name/{tableName}`: Removes association mappings from a junction table by junction table name. Requires `ManyToManyInsertRequest`.

### Dynamic CRUD Operations (`/api/data/*` & `/api/queries/*`)
* `POST /api/queries/select`: Executes a complex data fetch request against a dynamic table. Requires a `QueryRequest` mapping fields, conditions, sort orders, and recursive nested relations mappings (can target shadow logs using the `audit` request array).
* `GET /api/data/{tableName}`: Paginated fetch of table rows. Supports parameters `page`, `size`, and `show_sensitive` (privileged decryption toggle).
* `GET /api/data/{tableName}/{id}`: Fetches a single record by row ID. Supports `show_sensitive`.
* `POST /api/data/insert`: Writes a data payload row to a target table. Supports nested relation records.
* `PUT /api/data/{tableName}/{id}`: Modifies columns of the record with the matching row ID.
* `DELETE /api/data/{tableName}/{id}`: Deletes the record with the matching row ID.

### Kafka Synchronization (`/api/kafka-mappings/*`) (`KAFKA_ENGINEER` role required)
* `POST /api/kafka-mappings`: Binds a table to a Kafka topic configuration. Requires query parameters `tableName`, `topicName`, and `direction` (`INBOUND` or `OUTBOUND`); the topic is looked up by name and auto-created (`sys_kafka_topics`) if it doesn't already exist.
* `GET /api/kafka-mappings`: Lists all Kafka table mappings.
* `GET /api/kafka-mappings/{id}`: Fetches a single mapping by its ID.
* `PUT /api/kafka-mappings/{id}`: Updates a mapping's table, topic, direction, or active state. Requires query parameters `tableName`, `topicName`, `direction`, `active`; reconciles the live `INBOUND` consumer subscription if the topic, direction, or active state changes.
* `DELETE /api/kafka-mappings/{id}`: Soft-deletes the mapping (marks it inactive) and stops its active inbound listener container if it had one.

### Ad-Hoc Script Execution (`/api/script`) (`SCRIPT_ENGINEER` role required)
* `POST /api/script`: Executes a JavaScript payload on the caller's behalf and returns its result alongside captured `console` output. Requires a `ScriptExecuteRequest` payload (`script`, optional `debug_enabled`). Returns `403 SCRIPT_EXECUTION_DISABLED` when the `production` Spring profile is active.

### System Script (Hook) Management (`/api/system-scripts/*`) (`SCRIPT_ENGINEER` or `KAFKA_ENGINEER` role required)
* `POST /api/system-scripts`: Creates a persisted hook script for a table (`script_type=DB`, requires `table_id`) or a Kafka topic (`script_type=KAFKA`, requires `topic_id`) - exactly one of the two must be set. Requires a `ScriptCreateRequest` payload.
* `GET /api/system-scripts`: Lists all persisted hook scripts.
* `GET /api/system-scripts/{id}`: Fetches a hook script by its ID.
* `PUT /api/system-scripts/{id}`: Updates a hook script's body. Requires a `ScriptUpdateRequest` payload (`script_body`).
* `DELETE /api/system-scripts/{id}`: Deletes a hook script.

### System Administration (`/api/system/*`) (`ADMIN` group required)
* `POST /api/system/reset-database`: Drops and recreates the database's baseline Flyway schema. Requires query parameter `confirm=yes-reset-db`.
* `POST /api/system/evict-cache`: Evicts all cached table metadata and relation lookups from Redis.

---

## Model Context Protocol (MCP) Integration

The **Model Context Protocol (MCP)** is an open standard designed to enable Large Language Models (LLMs) and AI assistants to securely connect to external data sources, business logic APIs, and development environments. 

This project integrates Spring AI's MCP starter libraries to expose its dynamic database catalog as actionable AI "skills". When an AI assistant (like Claude Desktop or Google Antigravity) connects to these servers, it discovers the exposed tools automatically, letting the AI read, analyze, and mutate data dynamically using natural language.

### 1. Live-Database MCP Server (`mcp-server`)
Connects AI assistants directly to the live application database over standard input/output (`stdio`) streams. Read tools (schema, data, relations, users) require no authentication; write and administrative tools require a valid Personal Access Token (PAT, configured via `--mcp.pat=<token>` at launch) and, where applicable, the corresponding group membership - the same authorization rules the REST endpoints enforce, reproduced at the tool layer since MCP calls bypass Spring Security entirely.

* **Table Metadata & Schemas**:
  * `getAllTables`: Get a paginated list of all active tables in the database registry.
  * `getTableById`: Fetch table metadata definitions using its internal system ID.
  * `getTableByName`: Fetch table metadata definitions using the table name.
  * `generateSchemaForTable`: Generates a standard JSON Schema layout for a specific table name.
  * `createTable`, `deleteTableByName`, `logSchemaChange`: create/drop tables and log manual DDL changes. Requires a PAT.
* **Dynamic Queries & Data Mutations**:
  * `executeSelect`: Executes a complex SQL select query (with filter conditions, sort directives, and recursive nested relations).
  * `getTableData`: Pulls paginated raw data rows from a specific table name.
  * `findRowById`: Finds a specific row in a dynamic table using its primary key ID.
  * `insertRow`, `updateRowById`, `deleteRowById`: mutate rows (supports nested relation records). Requires a PAT.
* **Relational Mapping**:
  * `getAllRelations`: Get a list of all established relationships across the database catalog.
  * `getRelationsForTable`: Get all resolved relations (Forward, Reverse, M2M) linked to a specific table name.
  * `createOneToOneRelation`, `createManyToOneRelation`, `createManyToManyRelation`, `insertManyToManyDataById`/`insertManyToManyDataByName`, `deleteManyToManyDataById`/`deleteManyToManyDataByName`: create relations and manage junction table data. Requires a PAT.
* **User Catalog**:
  * `getAllUsers`: Get a paginated list of all registered application users.
  * `getUserById`: Fetch user metadata records using their system user ID.
  * `getUserByName`: Fetch user metadata records using their exact username.
  * `findByUsername`: Find a user entity by their username string.
  * `createUser`, `deleteUserById`: manage user accounts. Requires a PAT and the `ADMIN` group.
* **Scripting**:
  * `executeScript`: executes an ad-hoc JavaScript payload. Requires a PAT and the `SCRIPT_ENGINEER` group; Chrome DevTools debugging is not available over MCP.
  * `createScript`, `updateScript`, `deleteScript`, `getScript`, `listScripts`: manage persisted hook scripts (`sys_scripts`). Create/update/delete require a PAT and the `SCRIPT_ENGINEER` (DB scripts) or `KAFKA_ENGINEER` (Kafka scripts) group.
* **Kafka Mapping Management**:
  * `createKafkaMapping`, `updateKafkaMapping`, `removeKafkaMapping`: manage table-to-topic mappings. Requires a PAT and the `KAFKA_ENGINEER` group.
  * `getKafkaMapping`, `listKafkaMappings`: read mapping configuration.
* **Database Administration**:
  * `resetSandboxDatabaseToDefault`: drops and recreates the connected database's baseline Flyway schema. Requires a PAT, the `ADMIN` group, and `confirm="yes-reset-db"`.
  * `evictAllCache`: evicts cached table metadata and relations from Redis. Requires a PAT and the `ADMIN` group.

### 2. Write-Capable Sandbox MCP Server (`sandbox-mcp`)
An isolated workspace enabling AI agents to experiment with schema design, relationship wiring, and mutations against a dedicated sandbox database. Most tools here operate as a fixed system user with no PAT required, by design, to allow unrestricted experimentation - the exceptions are database-wide administrative actions and scripting, which still enforce the same PAT/group checks as the live server since the underlying services (`DatabaseManagementService`, `ScriptExecutionService`, `ScriptManagementService`) apply those checks unconditionally, regardless of which module calls them.

* **Sandbox Administration**:
  * `resetSandboxDatabaseToDefault`: Drops all elements in the sandbox schema and runs Flyway migrations to recreate the baseline. Requires a PAT, the `ADMIN` group, and `confirm="yes-reset-db"`.
  * `evictAllCache`: Evicts cached table metadata and relations from Redis. Requires a PAT and the `ADMIN` group.
  * `copyLiveDatabaseToSandbox`: Copies the entire schema layouts and data rows from the live database into the sandbox container. Requires `confirm="yes-overwrite-sandbox"`.
* **Schema Design Tools** (no PAT required):
  * `createTable`: Creates a dynamic physical table layout and registers its columns and configurations.
  * `deleteTableByName`: Drops a physical table catalog and cascades registry cleanup.
  * `logSchemaChange`: Manually appends a raw DDL command change log entry.
* **Data Mutation Tools** (no PAT required):
  * `insertRow`: Inserts a data record into a table (supports recursive nested child insertions).
  * `updateRowById`: Modifies columns on a specific record by ID.
  * `deleteRowById`: Deletes a record from a table by ID.
* **Junction & Relation Builders** (no PAT required):
  * `createOneToOneRelation`: Establishes a 1:1 direct mapping between two tables.
  * `createManyToOneRelation`: Establishes a N:1 dynamic relationship mapping.
  * `createManyToManyRelation`: Creates a N:M relationship mapping (creates intermediate `_jt` junction tables).
  * `insertManyToManyDataById` / `insertManyToManyDataByName`: Inserts mapping rows into dynamic junction tables.
  * `deleteManyToManyDataById` / `deleteManyToManyDataByName`: Deletes mapping rows from dynamic junction tables.
* **Dynamic Queries & Read Operations**:
  * Mirrors the same query tools (`getAllTables`, `getTableById`, `getTableByName`, `generateSchemaForTable`, `executeSelect`, `getTableData`, `findRowById`, `getAllRelations`, `getRelationsForTable`, `getAllUsers`, `getUserById`, `getUserByName`, `findByUsername`) to read state directly from the sandbox database.
* **User Management** (no PAT required):
  * `createUser`: Registers a new user.
  * `deleteUserById`: Deletes a user account by ID.
* **Scripting**:
  * `executeScript`: executes an ad-hoc JavaScript payload against the sandbox database. Requires a PAT and the `SCRIPT_ENGINEER` group.
  * `createScript`, `updateScript`, `deleteScript`, `getScript`, `listScripts`: manage persisted hook scripts against the sandbox database. Create/update/delete require a PAT and the `SCRIPT_ENGINEER` (DB scripts) or `KAFKA_ENGINEER` (Kafka scripts) group.
* **Kafka Mapping Management** (no PAT required):
  * `createKafkaMapping`, `updateKafkaMapping`, `removeKafkaMapping`, `getKafkaMapping`, `listKafkaMappings`: manage table-to-topic mappings against the sandbox database.

### 3. Client Integration (Configuration Example)
To connect an AI assistant (like Claude Desktop or the Antigravity IDE) to these MCP servers, declare them in the client's `mcp_config.json` configuration file:

```json
{
  "mcpServers": {
    "spring-mcp-server": {
      "command": "java",
      "args": [
        "-jar",
        "C:/path/to/project/mcp-server/target/mcp-server-0.0.1-SNAPSHOT.jar",
        "--mcp.pat=<personal-access-token>"
      ]
    },
    "spring-sandbox-mcp": {
      "command": "java",
      "args": [
        "-jar",
        "C:/path/to/project/sandbox-mcp/target/sandbox-mcp-0.0.1-SNAPSHOT.jar",
        "--mcp.pat=<personal-access-token>"
      ]
    }
  }
}
```
The `--mcp.pat` argument is optional for both servers. On `mcp-server` it's required to unlock write/administrative tools (table and data mutations, user/script/Kafka-mapping management, database reset/cache eviction) - read-only tools work without it. On `sandbox-mcp` it's only required for the scripting and administrative tools, since most sandbox tools intentionally run as a fixed system user without a PAT. Generate a token via `POST /api/auth/pat`.

---

## Setup and Bootstrapping

### Prerequisites
* Java Development Kit (JDK) 21 or higher.
* PostgreSQL database instance.
* Redis server (optional, database query caching fallback active if unavailable).
* Apache Kafka broker (optional, required for streaming sync).
* **Docker Engine** / **Docker Desktop** (required to host short-lived container instances automatically during test executions).

### Environment Configuration
The application requires the following environment variables to be configured:

```bash
# Main Database Configuration
LOCAL_DB_NAME=jdbc:postgresql://localhost:5432/spring_rest_project_db
LOCAL_DB_USER=your_postgres_user
LOCAL_DB_PASS=your_postgres_password

# Authentication Setup
app.jwt.secret=your_super_secret_jwt_key_at_least_256_bits_long
APP_ADMIN_USER=admin
APP_ADMIN_PASS=admin_password

# Sandbox Database Configuration
SANDBOX_DB_URL=jdbc:postgresql://localhost:5432/sandbox_project_db
SANDBOX_DB_USER=your_sandbox_user
SANDBOX_DB_PASS=your_sandbox_password
```

Additional optional properties (with defaults, set in `application.properties`):
```properties
# Script Execution Configuration (ad-hoc and hook scripts)
script.execution.timeout-ms=5000
script.execution.memory-limit-mb=64

# MCP Personal Access Token (unlocks write/administrative MCP tools; see mcp-server/sandbox-mcp launch args)
mcp.pat=

# Spring Profile (setting this to "production" disables ad-hoc script execution via /api/script)
spring.profiles.active=
```

### Running the Application

To clean the modules and compile all targets:
```bash
./mvnw clean compile
```

To run the REST API server:
```bash
./mvnw spring-boot:run -pl api
```

To run the read-only MCP Server (stdio communication):
```bash
./mvnw spring-boot:run -pl mcp-server
```

To run the write-capable Sandbox MCP Server (stdio communication):
```bash
./mvnw spring-boot:run -pl sandbox-mcp
```

### Running Tests
The test suite utilizes **Testcontainers** which communicates with the local Docker daemon to dynamically boot up fully isolated, short-lived containers containing a test PostgreSQL instance, a test Redis server, and a Kafka broker instance. 
* **Zero Configuration**: Developers do not need to configure schema configurations or start databases manually.
* **Automatic Teardown**: Once the test cycles finish, the containers are automatically stopped and pruned, leaving the local machine cleanly intact.

```bash
./mvnw clean test
```

---
