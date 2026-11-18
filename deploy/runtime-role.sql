-- Run as a database administrator using psql variables database, schema, runtime_role.
-- Set the password interactively with \password; never put it in this file.
\set ON_ERROR_STOP on
CREATE ROLE :"runtime_role" LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE;
GRANT CONNECT ON DATABASE :"database" TO :"runtime_role";
GRANT USAGE ON SCHEMA :"schema" TO :"runtime_role";
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
  :"schema".app_users, :"schema".auth_sessions, :"schema".login_attempts,
  :"schema".teams, :"schema".players, :"schema".player_stats,
  :"schema".bid_logs, :"schema".live_auctions, :"schema".auction_events
TO :"runtime_role";
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA :"schema" TO :"runtime_role";
-- No CREATE or schema ownership is granted.
