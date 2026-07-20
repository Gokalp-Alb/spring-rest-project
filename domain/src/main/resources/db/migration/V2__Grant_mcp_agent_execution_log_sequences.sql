-- V1's `GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO mcp_agent`
-- ran before execution_logs/execution_log_entries were created, so their BIGSERIAL
-- sequences were never covered - INSERTs via mcp_agent fail with
-- "permission denied for sequence execution_logs_id_seq" despite mcp_agent already
-- holding table-level INSERT on execution_logs (granted later in V1).

GRANT USAGE, SELECT, UPDATE ON execution_logs_id_seq TO mcp_agent;
GRANT USAGE, SELECT, UPDATE ON execution_log_entries_log_id_seq TO mcp_agent;
