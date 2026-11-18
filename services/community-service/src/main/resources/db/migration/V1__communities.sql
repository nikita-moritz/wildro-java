-- SPEC.md §18.3: a group of people who go out together and run events jointly.

CREATE TABLE communities (
    id                bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- The public address, /community/<slug>. Indexed by search engines, so it never moves.
    slug              text        NOT NULL UNIQUE,
    name              text        NOT NULL,
    description       text        NOT NULL,
    -- City or region in words; where the group goes out.
    area              text        NOT NULL,
    -- The language the texts are written in, as for own events (§18.2, question 3).
    locale            text        NOT NULL,
    status            text        NOT NULL DEFAULT 'ACTIVE',
    suspension_reason text,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    closed_at         timestamptz,

    CONSTRAINT communities_name_length CHECK (char_length(name) BETWEEN 2 AND 80),
    CONSTRAINT communities_description_length CHECK (char_length(description) BETWEEN 1 AND 5000),
    CONSTRAINT communities_area_length CHECK (char_length(area) BETWEEN 2 AND 120),
    CONSTRAINT communities_locale_known CHECK (locale IN ('de', 'en', 'ru')),
    -- ACTIVE, CLOSED by its owner (kept visible so links do not break), or
    -- SUSPENDED by moderation (off the site, reason kept).
    CONSTRAINT communities_status_known CHECK (status IN ('ACTIVE', 'CLOSED', 'SUSPENDED')),
    CONSTRAINT communities_suspended_has_reason CHECK ((status = 'SUSPENDED') = (suspension_reason IS NOT NULL)),
    CONSTRAINT communities_closed_has_time CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL))
);

-- Links to the group's own channels, by the link rules of SPEC.md §0: https
-- only, at most five, none twice. Order as written.
CREATE TABLE community_links (
    community_id bigint  NOT NULL REFERENCES communities (id) ON DELETE CASCADE,
    position     integer NOT NULL,
    url          text    NOT NULL,
    PRIMARY KEY (community_id, position),
    CONSTRAINT community_links_https CHECK (url ~ '^https://[^\s]+$'),
    CONSTRAINT community_links_position CHECK (position BETWEEN 0 AND 4),
    UNIQUE (community_id, url)
);

-- Roles live on the membership, not on the person (§18.3): in every community
-- one has a role of one's own, meaningless outside it.
CREATE TABLE memberships (
    community_id bigint      NOT NULL REFERENCES communities (id) ON DELETE CASCADE,
    user_id      text        NOT NULL,
    role         text        NOT NULL,
    -- The name members see in the member list. Taken from the user service when
    -- the person joins or acts here; members only ever see names of members.
    display_name text        NOT NULL,
    joined_at    timestamptz NOT NULL DEFAULT now(),
    -- Since when in this role: ownership passes to the longest-serving ADMIN.
    role_since   timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (community_id, user_id),
    CONSTRAINT memberships_role_known CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER'))
);

-- Exactly one owner per community is held by this index plus the rule that
-- ownership only ever moves inside one transaction.
CREATE UNIQUE INDEX memberships_one_owner ON memberships (community_id) WHERE role = 'OWNER';
CREATE INDEX memberships_user_idx ON memberships (user_id);

-- Privileged actions, in the same transaction as the change (§18.4).
CREATE TABLE audit_log (
    id           bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at  timestamptz NOT NULL DEFAULT now(),
    actor        text        NOT NULL,
    action       text        NOT NULL,
    community_id bigint      NOT NULL,
    details      jsonb,
    CONSTRAINT audit_log_action_known CHECK (action IN (
        'COMMUNITY_CREATED', 'COMMUNITY_UPDATED', 'COMMUNITY_CLOSED', 'COMMUNITY_SUSPENDED',
        'COMMUNITY_REINSTATED', 'ROLE_CHANGED', 'MEMBER_REMOVED', 'OWNERSHIP_TRANSFERRED'
    ))
);

-- What the event service must carry out: a closed community's future events
-- are cancelled (§18.3). Pulled through /internal/outbox, never deleted here.
CREATE TABLE outbox (
    id           bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type         text        NOT NULL,
    community_id bigint      NOT NULL,
    payload      jsonb       NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT outbox_type_known CHECK (type IN ('community.closed'))
);

-- How far this service has read another one's outbox. Moved in the same
-- transaction as what an entry caused, so nothing is applied twice or skipped.
CREATE TABLE consumer_cursor (
    source  text   PRIMARY KEY,
    last_id bigint NOT NULL
);
