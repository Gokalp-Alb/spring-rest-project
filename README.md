Dynamic Schema Engine & Virtual Runtime Storage
A highly flexible, metadata-driven Spring Boot engine designed to treat a strict relational database (PostgreSQL).

In standard Java applications, database structures are entirely rigid—bound to compile-time Hibernate or JPA @Entity blueprints. This system completely bypasses those constraints. By storing database schemas as structural metadata and leveraging programmatic, runtime SQL generation, administrators can create tables, mutate layouts, append columns, and run complex CRUD operations instantly through JSON API boundaries—without a single line of code compilation or a system reboot.

Core Architecture & System Features
The architecture behaves as a dynamic virtualization abstraction bridge sitting directly over your raw database driver connection layer.

1. Metadata-Driven Schema Registry
Instead of mapping code files to physical tables, the application tracks your structural state inside two core system configuration tables: table_metadata and column_metadata.

Validation Registry: When any dynamic REST payload arrives, the engine performs a pre-flight metadata lookup. It verifies that the targeted table layout exists, and confirms that the user's input keys match the registered column definitions and data types before executing anything against your data blocks.

2. Dynamic SQL Generation Engine (DataService)
The heart of the runtime engine constructs pure, highly performant, parameterized prepared statement strings on the fly.

Position-Safe Parameters: It reflects over incoming JSON data maps, mapping field keys to columns and formatting placeholders:

SQL
INSERT INTO dynamic_table_name (column_a, column_b) VALUES (?, ?);
Performance Security: By routing all dynamic strings through JdbcTemplate utilizing positional parameters (?), the database optimizer calculates cacheable execution plans while providing native, iron-clad protection against SQL Injection vulnerabilities.

3. Contextual Data Masking & Redaction
Security is integrated directly into the dynamic query compilation layer using the DataEvaluationHelper.

Dynamic Projection Interception: The layout checker evaluates whether a specific table or data field is marked as sensitive in the registry.

If a non-privileged user attempts to pull the data, the system automatically replaces the requested query projection from a raw SELECT * format to an on-the-fly masked query string representation, safely obfuscating the data at the database extraction tier:

SQL
SELECT id, '********' AS sensitive_field_column FROM custom_table;
4. Fully Reconstructed Compliance Auditing (system_ddl_log)
Because schemas and data boundaries shift dynamically at runtime, standard database logs are insufficient.

The engine captures every structural execution block, runs it through a string parameter parser utility (rebuildFullSql), reconstructs the exact raw parameters back into human-readable literal SQL statements, and logs the execution event along with the modifying userId into a persistent compliance audit ledger.

5. Event-Driven Propagation Side-Car (Kafka integration)
To ensure external systems or microservices can mirror this engine's runtime state changes, the platform is equipped with an event propagation boundary.

Dynamic Consumer Threads: Utilizing a customized ConcurrentMessageListenerContainer manager, it reads data sync records from kafka_table_mappings and hot-swaps active background consumer worker pools on the fly to process incoming data packets natively.

Loop Prevention Guardrail: Changes applied by internal background processing workers execute under a unique system identifier (userId = 0L). The outbound publishing mechanism automatically drops events marked with this identifier, safely breaking potential infinite cross-system ping-pong message loops.

🚀 API Endpoint Architecture Overview
Every operation in the system is driven through standard, highly reusable API patterns.

Schema Mutations
POST /api/tables - Create a completely new custom table layout at runtime by passing a structural JSON matrix specifying column definitions, constraints, and sensitivity configurations.

DELETE /api/tables/{tableName} - Safely tear down a custom database space and invalidate its metadata history records.

Row Manipulations (/api/data/*)
POST /api/data/insert - Write a row to any table layout by specifying the target table and passing a raw key-value pair payload map.

PUT /api/data/update - Mutate column properties on matching indices.

POST /api/data/query - Fetch table data (automatically applying sensitive data redaction masking algorithms if required by context rules).

Event Mapping Management
POST /api/kafka-mappings - Bind an existing runtime table to a Kafka topic configuration channel instantly for real-time data streaming inbound or outbound.

🛠️ Local Environment Bootstrap
Prerequisite Stack
Java 21+ (Engine utilizes modern Java Records for immutable, low-overhead DTO design)

PostgreSQL Engine (Hosts metadata structures and dynamic customer data partitions)

Apache Kafka Broker (Facilitates external application state notifications)
