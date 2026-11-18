-- SPEC.md §18.1: what wildro knows about a person is exactly three things, the
-- elvix id, the address and the name, plus the terms mark. Sign-in, sessions,
-- roles and blocking live in elvix and are not mirrored here.

CREATE TABLE users (
    -- The token's subject. Stable for life; the address may change, this does not.
    id                text        PRIMARY KEY,
    email             text,
    display_name      text,
    -- Last status elvix reported. Informational: the token is what lets a
    -- person in or not, and it carries the roles itself.
    status            text        NOT NULL DEFAULT 'ACTIVE',
    -- When elvix recorded the change the status came from, so a delivery that
    -- arrives late cannot overwrite a newer one.
    status_changed_at timestamptz,
    terms_version     text,
    terms_accepted_at timestamptz,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    -- Set when the account was deleted in elvix. The row stays so authorship
    -- links do not dangle; what identifies the person goes.
    erased_at         timestamptz,

    CONSTRAINT users_status CHECK (status IN ('ACTIVE', 'BANNED', 'PAUSED', 'INACTIVE', 'DELETED')),
    CONSTRAINT users_erased_holds_nothing CHECK (erased_at IS NULL OR (email IS NULL AND display_name IS NULL)),
    CONSTRAINT users_terms_pair CHECK ((terms_version IS NULL) = (terms_accepted_at IS NULL)),
    CONSTRAINT users_display_name_length CHECK (display_name IS NULL OR char_length(display_name) BETWEEN 2 AND 50)
);

-- elvix retries a delivery until it is acknowledged, so the same event can
-- arrive more than once. Its id is recorded in the transaction that applies it.
CREATE TABLE processed_webhook (
    event_id    text        PRIMARY KEY,
    type        text        NOT NULL,
    received_at timestamptz NOT NULL DEFAULT now()
);

-- Consequences other modules must carry out (SPEC.md §18.1, deletion), written
-- in the same transaction as the change that causes them, so a crash between
-- the two cannot lose one. Read by the relay that arrives with the first
-- consumer; until then rows accumulate, which is the point.
CREATE TABLE outbox (
    id           bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type         text        NOT NULL,
    user_id      text        NOT NULL,
    payload      jsonb       NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    published_at timestamptz
);

CREATE INDEX outbox_unpublished ON outbox (id) WHERE published_at IS NULL;
