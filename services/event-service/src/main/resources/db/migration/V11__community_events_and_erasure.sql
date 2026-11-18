-- Stage two, SPEC.md §18.3 and §18.4.

-- 1. An event owned by a community carries its address too, so its page can
-- link to the community without asking the community service.
ALTER TABLE events ADD COLUMN community_slug text;
ALTER TABLE events ADD CONSTRAINT events_community_slug_pair
    CHECK ((owner_community_id IS NULL) = (community_slug IS NULL));
CREATE INDEX events_owner_community_idx ON events (owner_community_id, passes_at) WHERE owner_community_id IS NOT NULL;

-- The limits count per account, so they follow the author, who is also the
-- owner of a personal event.
CREATE INDEX events_author_idx ON events (author_user_id, passes_at) WHERE author_user_id IS NOT NULL;

-- 2. The author's account was deleted (§18.1): their past personal events stay
-- without their name. The name is not rewritten into a placeholder; the page
-- reads this flag and says so in its own language.
ALTER TABLE events ADD COLUMN author_erased boolean NOT NULL DEFAULT false;

-- 3. How far this service has read the other services' outboxes. Moved in the
-- same transaction as what an entry caused (§18.4: late, but without loss and
-- without repeats).
CREATE TABLE consumer_cursor (
    source  text   PRIMARY KEY,
    last_id bigint NOT NULL
);
