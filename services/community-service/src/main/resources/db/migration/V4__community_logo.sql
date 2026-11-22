-- A group's logo or picture (SPEC.md §18.3, decided 2026-11-22: uploads with
-- moderation after the fact). The file lives on the server's disk under
-- MEDIA_DIR and is served by the proxy; the table keeps only its name, which
-- the service makes itself: <community id>-<12 hex of its SHA-256>.webp.
ALTER TABLE communities ADD COLUMN logo_file text;
ALTER TABLE communities ADD CONSTRAINT communities_logo_file_shape
    CHECK (logo_file IS NULL OR logo_file ~ '^[0-9]+-[0-9a-f]{12}\.webp$');

-- Moderation may take the picture alone down; the owner gets the reason by
-- email through the event service, like a suspension.
ALTER TABLE outbox DROP CONSTRAINT outbox_type_known;
ALTER TABLE outbox ADD CONSTRAINT outbox_type_known
    CHECK (type IN ('community.closed', 'community.suspended', 'community.logo_removed'));

ALTER TABLE audit_log DROP CONSTRAINT audit_log_action_known;
ALTER TABLE audit_log ADD CONSTRAINT audit_log_action_known CHECK (action IN (
    'COMMUNITY_CREATED', 'COMMUNITY_UPDATED', 'COMMUNITY_CLOSED', 'COMMUNITY_SUSPENDED',
    'COMMUNITY_REINSTATED', 'ROLE_CHANGED', 'MEMBER_REMOVED', 'OWNERSHIP_TRANSFERRED',
    'REPORT_DISMISSED', 'LOGO_CHANGED', 'LOGO_REMOVED'
));
