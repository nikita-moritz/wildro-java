-- Every event carries the coordinates of its start point, so proximity search is
-- a database index rather than application arithmetic (AGENTS.md, technical
-- requirements). The extension therefore belongs to the schema and is created by
-- a migration, not by hand on whichever database happens to be in front of us.
CREATE EXTENSION IF NOT EXISTS postgis;
