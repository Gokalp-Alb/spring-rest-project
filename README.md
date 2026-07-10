# Dynamic Schema Engine and Virtual Runtime Storage

A highly flexible, metadata-driven Spring Boot platform designed to bypass compile-time database boundaries. By storing database schemas as structural metadata and generating SQL dynamically at runtime, this system allows clients to create tables, alter structures, create complex relational models, and execute CRUD operations instantly through JSON API endpoints without requiring code recompilation or application reboots.

---

## Architecture Overview

The workspace is organized as a multi-module Maven project separating the core domain logic, web controllers, and Model Context Protocol (MCP) server adapters.

* **domain**: The core engine module. Contains the schema registry services, caching configurations (Redis), database migrations (Flyway), query parser helper classes, and transaction layers.
* **api**: The HTTP entry point module. Exposes standard REST endpoints for authentication, user management, schema mutations, dynamic data manipulations, and event integration.
* **mcp-server**: A stdio-based Model Context Protocol (MCP) server. Exposes a read-only subset of database querying and schema inspection tools to AI agents.
* **sandbox-mcp**: A stdio-based write-capable MCP server. Allows AI agents to interact with a dedicated sandbox database to test migrations, dynamic table creation, and insertions in isolation.

---

## Core Technologies and Libraries

* **Backend Framework**: Java 21 and Spring Boot 4.1.0. Preserves method parameters at compilation to facilitate runtime reflection.
* **Database & Persistence**: PostgreSQL, Spring JDBC (JdbcTemplate) for dynamic runtime queries, and JOOQ (Java Object-Oriented Querying) for system catalog queries. *This project runs purely on JOOQ and raw SQL template compiles without using JPA or Hibernate.*
* **Database Migration**: Flyway manages foundational system metadata and user tables.
* **Caching Layer**: Spring Data Redis configured with a custom **Jackson 3 (`tools.jackson`)** ObjectMapper for high-performance cache-aside caching.
* **Message Broker & Event Streaming**: Spring Kafka utilizing customized concurrent listener containers to hot-swap background consumer worker threads dynamically at runtime.
* **Security & Auth**: Spring Security utilizing JSON Web Tokens (JWT) through OAuth2 Resource Server boundaries.
* **Integration Testing (Docker & Testcontainers)**: Driven by **Testcontainers** to instantiate isolated, disposable infrastructure. It interacts with a local Docker daemon to automatically pull, run, and tear down short-lived PostgreSQL, Redis, and Kafka broker containers.

---

## Core System Features

### 1. Metadata-Driven Registry and Constraint Auditing
Instead of mapping compile-time Java classes directly to physical structures, layout states are maintained in registry catalog tables (`table_metadata` and `column_metadata`).
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

### 5. Dynamic DDL & Mutation Auditing (`system_ddl_log`)
To record how dynamic database spaces mutate, mutations write a transaction entry.
* **Schema Log**: DDL operations parse executions via `rebuildFullSql` to reconstruct the exact executed query string, recording the statement along with the performing user's context in the `system_ddl_log` repository.
* **Data Log**: For audited tables (`isAuditEnabled=true`), mutations (inserts, updates, deletes) automatically duplicate execution paths to record shadow historical records in corresponding target tables ending in `_log`.

### 6. Dynamic Query & Audit Query Engine
The SELECT engine supports standard data retrieval (`POST /api/queries/select`) with operators (such as `BETWEEN`, `BEFORE`, `AFTER`, `IN`, `LIKE`, etc.), sort directives, and column projection fields.
* **Audit Logs Querying**: If a table has auditing enabled (`isAuditEnabled`), clients can include an `audit` parameter block inside their select query. The compiler will map this select query against the shadow audit log table (ending in `_log`) rather than the active table, enabling clients to search for historic mutations, executed operation types (e.g. `POST`, `PUT`, `DELETE`), modified timeframes, or executing user IDs.

### 7. Masking and Privacy Obfuscation
Fields flagged as sensitive in the column metadata registry are intercepted during projection translation. If a non-privileged query request arrives, the generator rewrites the SQL projection from a standard selector to an on-the-fly masked representation (e.g. `'********' AS sensitive_field`), ensuring values are redacted before leaving the database layer.

### 8. Dynamic Event Synchronization
The platform includes an event synchronization interface mapping Kafka topics to database tables. It detects configuration changes in the background and spins up or terminates consumer worker threads dynamically without disrupting the main application container.

### 9. Loop Prevention Guardrail
Updates processed by background synchronization workers run under a distinct system context (`userId = 0L`). The outbound transaction publisher checks this identifier and drops matching events, preventing infinite state-propagation loops between connected systems.

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

### Kafka Synchronization (`/api/kafka-mappings/*`)
* `POST /api/kafka-mappings`: Binds a table to a Kafka topic configuration. Requires parameter bindings `tableName`, `kafkaTopic`, and `direction` (`INBOUND` or `OUTBOUND`).
* `DELETE /api/kafka-mappings/{id}`: Removes the configuration binding and stops active inbound listener containers.

---

## Model Context Protocol (MCP) Integration

The **Model Context Protocol (MCP)** is an open standard designed to enable Large Language Models (LLMs) and AI assistants to securely connect to external data sources, business logic APIs, and development environments. 

This project integrates Spring AI's MCP starter libraries to expose its dynamic database catalog as actionable AI "skills". When an AI assistant (like Claude Desktop or Google Antigravity) connects to these servers, it discovers the exposed tools automatically, letting the AI read, analyze, and mutate data dynamically using natural language.

### 1. Read-Only MCP Server (`mcp-server`)
Designed to give AI assistants safe, read-only visibility into database catalogs. It communicates via standard input/output (`stdio`) streams and exposes the following tools:

* **Table Metadata & Schemas**:
  * `getAllTables`: Get a paginated list of all active tables in the database registry.
  * `getTableById`: Fetch table metadata definitions using its internal system ID.
  * `getTableByName`: Fetch table metadata definitions using the table name.
  * `generateSchemaForTable`: Generates a standard JSON Schema layout for a specific table name.
* **Dynamic Queries & Selects**:
  * `executeSelect`: Executes a complex SQL select query (with filter conditions, sort directives, and recursive nested relations).
  * `getTableData`: Pulls paginated raw data rows from a specific table name.
  * `findRowById`: Finds a specific row in a dynamic table using its primary key ID.
* **Relational Mapping**:
  * `getAllRelations`: Get a list of all established relationships across the database catalog.
  * `getRelationsForTable`: Get all resolved relations (Forward, Reverse, M2M) linked to a specific table name.
* **User Catalog**:
  * `getAllUsers`: Get a paginated list of all registered application users.
  * `getUserById`: Fetch user metadata records using their system user ID.
  * `getUserByName`: Fetch user metadata records using their exact username.
  * `findByUsername`: Find a user entity by their username string.

### 2. Write-Capable Sandbox MCP Server (`sandbox-mcp`)
An isolated workspace enabling AI agents to experiment with schema design, relationship wiring, and mutations. It exposes write-capable tools alongside sandboxing controls:

* **Sandbox Administration**:
  * `resetSandboxDatabaseToDefault`: Drops all elements in the sandbox schema and runs Flyway migrations to recreate the baseline. Requires `confirm="yes-reset-sandbox"`.
  * `copyLiveDatabaseToSandbox`: Copies the entire schema layouts and data rows from the live database into the sandbox container. Requires `confirm="yes-overwrite-sandbox"`.
* **Schema Design Tools**:
  * `createTable`: Creates a dynamic physical table layout and registers its columns and configurations.
  * `deleteTableByName`: Drops a physical table catalog and cascades registry cleanup.
  * `logSchemaChange`: Manually appends a raw DDL command change log entry.
* **Data Mutation Tools**:
  * `insertRow`: Inserts a data record into a table (supports recursive nested child insertions).
  * `updateRowById`: Modifies columns on a specific record by ID.
  * `deleteRowById`: Deletes a record from a table by ID.
* **Junction & Relation Builders**:
  * `createOneToOneRelation`: Establishes a 1:1 direct mapping between two tables.
  * `createManyToOneRelation`: Establishes a N:1 dynamic relationship mapping.
  * `createManyToManyRelation`: Creates a N:M relationship mapping (creates intermediate `_jt` junction tables).
  * `insertManyToManyDataById` / `insertManyToManyDataByName`: Inserts mapping rows into dynamic junction tables.
  * `deleteManyToManyDataById` / `deleteManyToManyDataByName`: Deletes mapping rows from dynamic junction tables.
* **Dynamic Queries & Read Operations**:
  * Mirrors the same query tools (`getAllTables`, `getTableById`, `getTableByName`, `generateSchemaForTable`, `executeSelect`, `getTableData`, `findRowById`, `getAllRelations`, `getRelationsForTable`, `getAllUsers`, `getUserById`, `getUserByName`, `findByUsername`) to read state directly from the sandbox database.
* **User Management**:
  * `createUser`: Registers a new user.
  * `deleteUserById`: Deletes a user account by ID.

### 3. Client Integration (Configuration Example)
To connect an AI assistant (like Claude Desktop or the Antigravity IDE) to these MCP servers, declare them in the client's `mcp_config.json` configuration file:

```json
{
  "mcpServers": {
    "spring-read-mcp": {
      "command": "java",
      "args": [
        "-jar",
        "C:/path/to/project/mcp-server/target/mcp-server-0.0.1-SNAPSHOT.jar"
      ]
    },
    "spring-sandbox-mcp": {
      "command": "java",
      "args": [
        "-jar",
        "C:/path/to/project/sandbox-mcp/target/sandbox-mcp-0.0.1-SNAPSHOT.jar"
      ]
    }
  }
}
```

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

## Development Rules and Conventions

When developing or integrating new features on this codebase, adhere to the following design boundaries:

1. **Junction Table Lifecycles**: Many-to-Many junction tables (identifiable by ending in `_jt` and housing exactly two `MANY_TO_ONE` foreign keys) must be programmatically dropped when either of their parent tables is deleted, preserving relational consistency.
2. **Orphan Column Drops**: When a parent table is deleted, child foreign key reference columns must be explicitly dropped (`ALTER TABLE child_table DROP COLUMN fk_column CASCADE`) to prevent dynamic catalog pollution.
3. **Auditing Sync**: Schema mutations (`ALTER TABLE`) on audited tables must be synchronized to their corresponding shadow `_log` tables.
4. **Empty Dynamic Table Inserts**: PostgreSQL does not support `INSERT INTO table () VALUES ()`. When writing integration tests that define custom user table metadata schemas and seed them directly, ensure the schemas contain at least one user-defined column (e.g. `name`). This prevents empty insertion statement syntax failures in test contexts where dynamic system columns are bypassed or mocked.
5. **Spring Boot 4.x & Jackson 3 Compatibility**: Caching layers must strictly use Jackson 3 (`tools.jackson`) constructed via `JsonMapper.builder().findAndAddModules()` and use `BasicPolymorphicTypeValidator` to avoid serialization errors with dates or records.
