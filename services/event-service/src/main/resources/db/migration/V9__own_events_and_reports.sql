-- Stage two, SPEC.md §18.2: events people create themselves, reports, and the
-- moderation that answers them.

-- 1. Who owns an own event.
--
-- The owner is the person (owner_user_id) or, from §18.3 on, a community
-- (owner_community_id); the author is always the person who wrote it. Both are
-- elvix ids: the account lives in the user service, which this database does
-- not reach, so there is no foreign key to have.
ALTER TABLE events ADD COLUMN owner_user_id      text;
ALTER TABLE events ADD COLUMN owner_community_id bigint;
ALTER TABLE events ADD COLUMN author_user_id     text;

-- The one language an own event is written in (§18.2, question 3). The text is
-- stored under every locale so the read path keeps one row per event and
-- language; this column is what lets a page say "in the organiser's language".
ALTER TABLE events ADD COLUMN original_locale    text;

-- When it first went public, for the limit of five a day.
ALTER TABLE events ADD COLUMN published_at       timestamptz;

-- Why moderation took it down; shown to the owner, sent to them by email.
ALTER TABLE events ADD COLUMN suspension_reason  text;

ALTER TABLE events DROP CONSTRAINT events_status_known;
ALTER TABLE events ADD CONSTRAINT events_status_known
    CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED', 'SUSPENDED'));

ALTER TABLE events ADD CONSTRAINT events_own_has_owner CHECK (
    kind <> 'OWN' OR (
        (owner_user_id IS NOT NULL) <> (owner_community_id IS NOT NULL)
        AND author_user_id IS NOT NULL
        AND original_locale IS NOT NULL
    )
);
ALTER TABLE events ADD CONSTRAINT events_external_has_no_owner CHECK (
    kind <> 'EXTERNAL' OR (owner_user_id IS NULL AND owner_community_id IS NULL AND author_user_id IS NULL)
);
ALTER TABLE events ADD CONSTRAINT events_original_locale_known
    CHECK (original_locale IS NULL OR original_locale IN ('de', 'en', 'ru'));
ALTER TABLE events ADD CONSTRAINT events_suspended_has_reason
    CHECK ((status = 'SUSPENDED') = (suspension_reason IS NOT NULL));

-- "My events", and the two limits: published in the last day, upcoming now.
CREATE INDEX events_owner_user_idx ON events (owner_user_id, passes_at) WHERE owner_user_id IS NOT NULL;

-- 2. Reports (§18.2, DSA Art. 16). Anyone may file one, a guest included, so
-- the reporter is optional and nothing about their address is stored: the
-- rate limit lives in memory.
CREATE TABLE reports (
    id               bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id         bigint      NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    reason           text        NOT NULL,
    details          text        NOT NULL,
    reporter_user_id text,
    status           text        NOT NULL DEFAULT 'OPEN',
    created_at       timestamptz NOT NULL DEFAULT now(),
    resolved_at      timestamptz,
    resolved_by      text,

    CONSTRAINT reports_reason_known CHECK (reason IN (
        'SPAM', 'MISLEADING', 'DANGEROUS', 'ILLEGAL', 'PERSONAL_DATA', 'OTHER'
    )),
    CONSTRAINT reports_status_known CHECK (status IN ('OPEN', 'ACTIONED', 'DISMISSED')),
    CONSTRAINT reports_details_length CHECK (char_length(details) BETWEEN 1 AND 2000),
    CONSTRAINT reports_resolution_pair CHECK ((status = 'OPEN') = (resolved_at IS NULL))
);

CREATE INDEX reports_open_idx ON reports (created_at) WHERE status = 'OPEN';

-- 3. The statement of reasons the owner of a removed event must receive (DSA
-- Art. 17), queued in the transaction that removes it. Delivered by whatever
-- transport is configured; until one is, the rows wait.
CREATE TABLE notice_outbox (
    id                bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    kind              text        NOT NULL,
    recipient_user_id text        NOT NULL,
    event_id          bigint,
    payload           jsonb       NOT NULL,
    created_at        timestamptz NOT NULL DEFAULT now(),
    sent_at           timestamptz,

    CONSTRAINT notice_outbox_kind_known CHECK (kind IN ('EVENT_SUSPENDED'))
);

CREATE INDEX notice_outbox_unsent ON notice_outbox (id) WHERE sent_at IS NULL;

-- 4. The audit log learns the web channel and the moderation actions.
ALTER TABLE audit_log DROP CONSTRAINT audit_log_channel_known;
ALTER TABLE audit_log ADD CONSTRAINT audit_log_channel_known CHECK (channel IN ('CLI', 'PANEL', 'WEB'));

ALTER TABLE audit_log DROP CONSTRAINT audit_log_action_known;
ALTER TABLE audit_log ADD CONSTRAINT audit_log_action_known CHECK (action IN (
    'EVENT_CREATED', 'EVENT_UPDATED', 'EVENT_CANCELLED', 'EVENT_DELETED',
    'EVENT_PUBLISHED', 'EVENT_SUSPENDED', 'EVENT_REINSTATED', 'REPORT_DISMISSED'
));
