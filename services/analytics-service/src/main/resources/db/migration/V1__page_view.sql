-- One row per page view of wildro.app (SPEC.md §13). Nothing in here can name a
-- person: no IP address, no user agent, no cookie. visitor_hash is
-- sha256(daily salt + IP + user agent), truncated, and changes every UTC day, so
-- the same person cannot be followed from one day to the next.
CREATE TABLE page_view (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    viewed_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    path         TEXT        NOT NULL CHECK (char_length(path) BETWEEN 1 AND 500),
    locale       TEXT        CHECK (locale IN ('de', 'en', 'ru')),
    host         TEXT        NOT NULL CHECK (char_length(host) <= 253),
    referrer     TEXT        CHECK (char_length(referrer) <= 1000),
    utm_source   TEXT        CHECK (char_length(utm_source) <= 100),
    utm_medium   TEXT        CHECK (char_length(utm_medium) <= 100),
    utm_campaign TEXT        CHECK (char_length(utm_campaign) <= 100),
    visitor_hash TEXT        CHECK (visitor_hash IS NULL OR visitor_hash ~ '^[0-9a-f]{32}$')
);

-- Every read filters on a time range, and the retention sweep deletes by it.
CREATE INDEX page_view_viewed_at_idx ON page_view (viewed_at);

-- Sessions are cut per visitor in time order (a window over visitor_hash,
-- ordered by viewed_at); rows without a hash never take part.
CREATE INDEX page_view_visitor_idx ON page_view (visitor_hash, viewed_at) WHERE visitor_hash IS NOT NULL;
