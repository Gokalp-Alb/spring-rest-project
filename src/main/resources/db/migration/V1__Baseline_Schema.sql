-- SECURITY & USER INFRASTRUCTURE

CREATE TABLE app_users (
                           id SERIAL PRIMARY KEY,
                           active BOOLEAN DEFAULT TRUE,
                           password VARCHAR(500) NOT NULL,
                           role VARCHAR(50) NOT NULL,
                           username VARCHAR(255) NOT NULL UNIQUE
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

CREATE TABLE table_metadata (
                                id SERIAL PRIMARY KEY,
                                table_name VARCHAR(255) NOT NULL UNIQUE,
                                creator_id BIGINT,
                                created_date TIMESTAMP,
                                last_updater_id BIGINT,
                                last_changed_date TIMESTAMP
);

CREATE TABLE column_metadata (
                                 id SERIAL PRIMARY KEY,
                                 table_id INT REFERENCES table_metadata(id) ON DELETE CASCADE,
                                 column_name VARCHAR(255) NOT NULL,
                                 data_type VARCHAR(50) NOT NULL,
                                 creator_id BIGINT,
                                 created_date TIMESTAMP,
                                 last_updater_id BIGINT,
                                 last_changed_date TIMESTAMP,
                                 is_sensitive BOOLEAN DEFAULT FALSE,
                                 is_unique BOOLEAN DEFAULT FALSE,
                                 validation_regex VARCHAR(500)
);


-- AUDIT LOGGING & EVENT MAPPING

CREATE TABLE system_ddl_log (
                                id SERIAL PRIMARY KEY,
                                executed_at TIMESTAMP NOT NULL,
                                executed_sql TEXT NOT NULL,
                                table_name VARCHAR(255),
                                user_id BIGINT
);

CREATE TABLE kafka_table_mappings (
                                      id SERIAL PRIMARY KEY,
                                      active BOOLEAN DEFAULT TRUE,
                                      direction VARCHAR(50),
                                      kafka_topic VARCHAR(255) NOT NULL,
                                      table_name VARCHAR(255) NOT NULL
);