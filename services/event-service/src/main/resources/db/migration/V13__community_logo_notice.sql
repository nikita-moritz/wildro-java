-- A moderator may take down a group's picture alone (community.logo_removed);
-- the owner is told why like for a suspension (DSA Art. 17), by the same relay.
ALTER TABLE notice_outbox DROP CONSTRAINT notice_outbox_kind_known;
ALTER TABLE notice_outbox ADD CONSTRAINT notice_outbox_kind_known
    CHECK (kind IN ('EVENT_SUSPENDED', 'COMMUNITY_SUSPENDED', 'COMMUNITY_LOGO_REMOVED'));
