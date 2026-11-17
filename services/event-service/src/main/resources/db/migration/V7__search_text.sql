-- Text search over the public lists (SPEC.md §0, поиск).
--
-- search_text holds the organiser and the name and place of every translation,
-- folded by SearchText in Java: lower case, accents dropped. Like passes_at it
-- is the stored output of one Java rule and is written only through the entity,
-- so the rule is not restated here. V8 fills it for existing rows with the same
-- Java code, then makes it NOT NULL.
--
-- pg_trgm is a trusted extension, so a database owner may create it; it is
-- also pre-created by postgres in each environment, like postgis (AGENTS.md).
CREATE EXTENSION IF NOT EXISTS pg_trgm;

ALTER TABLE events ADD COLUMN search_text text;

-- A trigram index answers LIKE '%word%' and the word_similarity typo match
-- without reading every row.
CREATE INDEX events_search_text_trgm_idx ON events USING gin (search_text gin_trgm_ops);
