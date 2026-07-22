-- SECURITY & USER INFRASTRUCTURE

CREATE TABLE sys_app_users (
                           id BIGSERIAL PRIMARY KEY,
                           active BOOLEAN DEFAULT TRUE,
                           password VARCHAR(500) NOT NULL,
                           username VARCHAR(255) NOT NULL UNIQUE,
                           creator_id BIGINT,
                           created_date TIMESTAMP,
                           last_updater_id BIGINT,
                           last_changed_date TIMESTAMP,
                           is_restricted BOOLEAN DEFAULT FALSE
);

CREATE TABLE spring_session (
                                primary_id CHAR(36) PRIMARY KEY,
                                session_id CHAR(36) NOT NULL,
                                creation_time BIGINT NOT NULL,
                                last_access_time BIGINT NOT NULL,
                                max_inactive_interval INT NOT NULL,
                                expiry_time BIGINT NOT NULL,
                                principal_name VARCHAR(100)
);

CREATE UNIQUE INDEX spring_session_ix1 ON spring_session (session_id);
CREATE INDEX spring_session_ix2 ON spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON spring_session (principal_name);

CREATE TABLE spring_session_attributes (
                                           session_primary_id CHAR(36) NOT NULL REFERENCES spring_session(primary_id) ON DELETE CASCADE,
                                           attribute_name VARCHAR(200) NOT NULL,
                                           attribute_bytes BYTEA NOT NULL,
                                           CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name)
);

--DYNAMIC SYSTEM ENGINE METADATA

CREATE TABLE sys_table_metadata (
                                id BIGSERIAL PRIMARY KEY,
                                table_name VARCHAR(255) NOT NULL UNIQUE,
                                creator_id BIGINT,
                                created_date TIMESTAMP,
                                last_updater_id BIGINT,
                                last_changed_date TIMESTAMP,
                                is_audit_enabled BOOLEAN DEFAULT FALSE,
                                is_restricted BOOLEAN DEFAULT FALSE
);

CREATE TABLE sys_column_metadata (
                                 id BIGSERIAL PRIMARY KEY,
                                 table_id BIGINT REFERENCES sys_table_metadata(id) ON DELETE CASCADE,
                                 column_name VARCHAR(255) NOT NULL,
                                 data_type VARCHAR(50) NOT NULL,
                                 creator_id BIGINT,
                                 created_date TIMESTAMP,
                                 last_updater_id BIGINT,
                                 last_changed_date TIMESTAMP,
                                 is_sensitive BOOLEAN DEFAULT FALSE,
                                 is_unique BOOLEAN DEFAULT FALSE,
                                 validation_regex VARCHAR(500),
                                 is_restricted BOOLEAN DEFAULT FALSE
);


-- AUDIT LOGGING & EVENT MAPPING

CREATE TABLE sys_ddl_log (
                                id BIGSERIAL PRIMARY KEY,
                                executed_at TIMESTAMP NOT NULL,
                                executed_sql TEXT NOT NULL,
                                table_name VARCHAR(255),
                                user_id BIGINT,
                                creator_id BIGINT,
                                created_date TIMESTAMP,
                                last_updater_id BIGINT,
                                last_changed_date TIMESTAMP,
                                is_restricted BOOLEAN DEFAULT FALSE
);

CREATE TABLE sys_kafka_table_mappings (
                                      id BIGSERIAL PRIMARY KEY,
                                      active BOOLEAN DEFAULT TRUE,
                                      direction VARCHAR(50),
                                      kafka_topic VARCHAR(255) NOT NULL,
                                      table_name VARCHAR(255) NOT NULL,
                                      creator_id BIGINT,
                                      created_date TIMESTAMP,
                                      last_updater_id BIGINT,
                                      last_changed_date TIMESTAMP,
                                      is_restricted BOOLEAN DEFAULT FALSE
);

-- SYSTEM LOG TABLES FOR AUDITING

CREATE TABLE sys_app_users_log (
    log_id BIGSERIAL PRIMARY KEY,
    id BIGINT,
    active BOOLEAN,
    password VARCHAR(500),
    username VARCHAR(255),
    operation_type VARCHAR(50),
    executed_at TIMESTAMP,
    user_id BIGINT
);

CREATE TABLE sys_table_metadata_log (
    log_id BIGSERIAL PRIMARY KEY,
    id BIGINT,
    table_name VARCHAR(255),
    creator_id BIGINT,
    created_date TIMESTAMP,
    last_updater_id BIGINT,
    last_changed_date TIMESTAMP,
    is_audit_enabled BOOLEAN,
    operation_type VARCHAR(50),
    executed_at TIMESTAMP,
    user_id BIGINT
);

CREATE TABLE sys_column_metadata_log (
    log_id BIGSERIAL PRIMARY KEY,
    id BIGINT,
    table_id BIGINT,
    column_name VARCHAR(255),
    data_type VARCHAR(50),
    creator_id BIGINT,
    created_date TIMESTAMP,
    last_updater_id BIGINT,
    last_changed_date TIMESTAMP,
    is_sensitive BOOLEAN,
    is_unique BOOLEAN,
    validation_regex VARCHAR(500),
    operation_type VARCHAR(50),
    executed_at TIMESTAMP,
    user_id BIGINT
);

CREATE TABLE sys_kafka_table_mappings_log (
    log_id BIGSERIAL PRIMARY KEY,
    id BIGINT,
    active BOOLEAN,
    direction VARCHAR(50),
    kafka_topic VARCHAR(255),
    table_name VARCHAR(255),
    operation_type VARCHAR(50),
    executed_at TIMESTAMP,
    user_id BIGINT
);

CREATE TABLE sys_relation_metadata (
    id BIGSERIAL PRIMARY KEY,
    relation_type VARCHAR(50) NOT NULL,
    source_table VARCHAR(255) NOT NULL,
    source_column VARCHAR(255),
    target_table VARCHAR(255) NOT NULL,
    target_column VARCHAR(255),
    junction_table VARCHAR(255),
    source_delete_policy VARCHAR(50),
    target_delete_policy VARCHAR(50),
    creator_id BIGINT,
    created_date TIMESTAMP,
    last_updater_id BIGINT,
    last_changed_date TIMESTAMP,
    is_restricted BOOLEAN DEFAULT FALSE
);

CREATE TABLE sys_relation_metadata_log (
    log_id BIGSERIAL PRIMARY KEY,
    id BIGINT,
    relation_type VARCHAR(50),
    source_table VARCHAR(255),
    source_column VARCHAR(255),
    target_table VARCHAR(255),
    target_column VARCHAR(255),
    junction_table VARCHAR(255),
    source_delete_policy VARCHAR(50),
    target_delete_policy VARCHAR(50),
    creator_id BIGINT,
    created_date TIMESTAMP,
    last_updater_id BIGINT,
    last_changed_date TIMESTAMP,
    operation_type VARCHAR(50),
    executed_at TIMESTAMP,
    user_id BIGINT
);

-- PERSONAL ACCESS TOKENS FOR MCP SECURE CONNECTIONS

CREATE TABLE sys_personal_access_tokens (
                                        id BIGSERIAL PRIMARY KEY,
                                        token_hash VARCHAR(255) NOT NULL UNIQUE,
                                        name VARCHAR(100) NOT NULL,
                                        user_id BIGINT NOT NULL REFERENCES sys_app_users(id) ON DELETE CASCADE,
                                        expires_at TIMESTAMP NOT NULL,
                                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
                                        last_used_at TIMESTAMP,
                                        creator_id BIGINT,
                                        created_date TIMESTAMP,
                                        last_updater_id BIGINT,
                                        last_changed_date TIMESTAMP,
                                        is_restricted BOOLEAN DEFAULT FALSE
);

-- MCP SYSTEM AGENT ROLE
DO
$$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'mcp_agent') THEN
        CREATE ROLE mcp_agent LOGIN PASSWORD '${mcp_password}';
    END IF;

    EXECUTE format('GRANT CONNECT ON DATABASE %I TO mcp_agent', current_database());
END
$$;

-- MCP SYSTEM AGENT ACCOUNT
INSERT INTO sys_app_users (id, username, password, active, creator_id, created_date, last_updater_id, last_changed_date, is_restricted)
VALUES (1, 'mcp_agent', '!disabled', false, 0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, true)
ON CONFLICT (id) DO NOTHING;

SELECT setval('sys_app_users_id_seq', (SELECT MAX(id) FROM sys_app_users));

-- SCRIPTING ENGINE EXECUTION LOGS

CREATE TABLE sys_execution_logs (
    id BIGSERIAL PRIMARY KEY,
    execution_id VARCHAR(255) NOT NULL UNIQUE,
    script TEXT NOT NULL,
    caller VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    output TEXT,
    duration_ms BIGINT,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    creator_id BIGINT,
    created_date TIMESTAMP,
    last_updater_id BIGINT,
    last_changed_date TIMESTAMP,
    is_restricted BOOLEAN DEFAULT FALSE
);

CREATE TABLE sys_execution_log_entries (
    log_id BIGSERIAL PRIMARY KEY,
    execution_id VARCHAR(255) NOT NULL REFERENCES sys_execution_logs(execution_id),
    level VARCHAR(20) NOT NULL,
    message TEXT NOT NULL,
    logged_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    sequence_number INT NOT NULL,
    stack_trace TEXT,
    creator_id BIGINT,
    created_date TIMESTAMP,
    last_updater_id BIGINT,
    last_changed_date TIMESTAMP,
    is_restricted BOOLEAN DEFAULT FALSE
);

-- USER GROUPS (many-to-many)

CREATE TABLE sys_user_groups (
    id BIGSERIAL PRIMARY KEY,
    group_name VARCHAR(50) NOT NULL UNIQUE,
    creator_id BIGINT,
    created_date TIMESTAMP,
    last_updater_id BIGINT,
    last_changed_date TIMESTAMP,
    is_restricted BOOLEAN DEFAULT FALSE
);

CREATE TABLE sys_user_groups_log (
    log_id BIGSERIAL PRIMARY KEY,
    id BIGINT,
    group_name VARCHAR(50),
    creator_id BIGINT,
    created_date TIMESTAMP,
    last_updater_id BIGINT,
    last_changed_date TIMESTAMP,
    operation_type VARCHAR(50),
    executed_at TIMESTAMP,
    user_id BIGINT
);

CREATE TABLE sys_app_users_sys_user_groups_jt (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES sys_app_users(id) ON DELETE CASCADE,
    group_id BIGINT NOT NULL REFERENCES sys_user_groups(id) ON DELETE CASCADE,
    creator_id BIGINT,
    created_date TIMESTAMP,
    last_updater_id BIGINT,
    last_changed_date TIMESTAMP,
    is_restricted BOOLEAN DEFAULT FALSE,
    UNIQUE (user_id, group_id)
);

INSERT INTO sys_user_groups (group_name, creator_id, created_date, last_updater_id, last_changed_date, is_restricted)
VALUES
    ('ADMIN', 0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, true),
    ('REGISTERED_USER', 0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, true),
    ('SCRIPT_ENGINEER', 0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, true),
    ('KAFKA_ENGINEER', 0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, true),
    ('MCP_AGENT', 0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, true),
    ('DATABASE_ADMIN', 0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, true);

INSERT INTO sys_app_users_sys_user_groups_jt (user_id, group_id, creator_id, created_date, last_updater_id, last_changed_date, is_restricted)
SELECT 1, id, 0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, true
FROM sys_user_groups WHERE group_name = 'MCP_AGENT';

-- SYSTEM RELATIONS (hand-created — sys_ tables never go through the guarded RelationService)

INSERT INTO sys_relation_metadata (relation_type, source_table, source_column, target_table, target_column, junction_table, source_delete_policy, target_delete_policy, creator_id, created_date, last_updater_id, last_changed_date, is_restricted)
VALUES ('MANY_TO_MANY', 'sys_app_users', NULL, 'sys_user_groups', NULL, 'sys_app_users_sys_user_groups_jt', 'CASCADE', 'CASCADE', 0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, true);

INSERT INTO sys_relation_metadata (relation_type, source_table, source_column, target_table, target_column, junction_table, source_delete_policy, target_delete_policy, creator_id, created_date, last_updater_id, last_changed_date, is_restricted)
VALUES ('MANY_TO_ONE', 'sys_execution_log_entries', 'execution_id', 'sys_execution_logs', 'execution_id', NULL, 'RESTRICT', NULL, 0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, true);

-- SYSTEM METADATA REGISTRY
-- Registers every system-managed table (and its columns) into sys_table_metadata / sys_column_metadata,
-- including this pair registering themselves. Uses information_schema rather than hand-listing every
-- column, since the alternative is hundreds of near-identical literal INSERT statements.
-- Excluded per spec: flyway_schema_history, sys_personal_access_tokens, spring_session, spring_session_attributes.
-- is_audit_enabled is set for base tables that have a corresponding _log table; the _log tables
-- themselves (and tables with no log counterpart) are not audit-enabled.
-- is_sensitive is set for any column literally named 'password' (sys_app_users, sys_app_users_log).

DO
$$
DECLARE
    sys_tables TEXT[] := ARRAY[
        'sys_app_users', 'sys_app_users_log',
        'sys_table_metadata', 'sys_table_metadata_log',
        'sys_column_metadata', 'sys_column_metadata_log',
        'sys_ddl_log',
        'sys_kafka_table_mappings', 'sys_kafka_table_mappings_log',
        'sys_relation_metadata', 'sys_relation_metadata_log',
        'sys_execution_logs', 'sys_execution_log_entries',
        'sys_user_groups', 'sys_user_groups_log', 'sys_app_users_sys_user_groups_jt'
    ];
    audited_tables TEXT[] := ARRAY[
        'sys_app_users',
        'sys_table_metadata',
        'sys_column_metadata',
        'sys_kafka_table_mappings',
        'sys_relation_metadata',
        'sys_user_groups'
    ];
    tbl TEXT;
    new_table_id BIGINT;
    col RECORD;
BEGIN
    FOREACH tbl IN ARRAY sys_tables LOOP
        INSERT INTO sys_table_metadata (table_name, creator_id, created_date, last_updater_id, last_changed_date, is_audit_enabled, is_restricted)
        VALUES (tbl, 0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, tbl = ANY(audited_tables), true)
        RETURNING id INTO new_table_id;

        FOR col IN
            SELECT column_name, data_type
            FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = tbl
            ORDER BY ordinal_position
        LOOP
            INSERT INTO sys_column_metadata (table_id, column_name, data_type, creator_id, created_date, last_updater_id, last_changed_date, is_sensitive, is_unique, is_restricted)
            VALUES (new_table_id, col.column_name, col.data_type, 0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, col.column_name = 'password', false, true);
        END LOOP;
    END LOOP;
END
$$;

-- MCP AGENT AUTHORIZATION GRANTS
-- Runs last, after every table/sequence in this migration already exists, so the
-- "ALL TABLES"/"ALL SEQUENCES" grants below cover everything in one pass.

GRANT USAGE ON SCHEMA public TO mcp_agent;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO mcp_agent;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO mcp_agent;
GRANT UPDATE ON sys_personal_access_tokens TO mcp_agent;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO mcp_agent;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON SEQUENCES TO mcp_agent;

-- MCP WRITE-CAPABILITY FOR DDL REGISTRY & SCHEMA OPERATIONS
GRANT CREATE ON SCHEMA public TO mcp_agent;
GRANT INSERT, UPDATE, DELETE ON
    sys_table_metadata,
    sys_table_metadata_log,
    sys_column_metadata,
    sys_column_metadata_log,
    sys_relation_metadata,
    sys_relation_metadata_log,
    sys_ddl_log,
    sys_personal_access_tokens,
    sys_kafka_table_mappings,
    sys_kafka_table_mappings_log
TO mcp_agent;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO mcp_agent;

GRANT SELECT, INSERT, UPDATE ON sys_execution_logs TO mcp_agent;
GRANT SELECT, INSERT, UPDATE ON sys_execution_log_entries TO mcp_agent;
