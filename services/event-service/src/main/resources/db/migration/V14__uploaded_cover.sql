-- A picture the organiser of an own event uploads (decided 2026-11-22, the same
-- model as a group's picture): scaled and stripped of metadata by the frontend's
-- server, checked and written to MEDIA_DIR here, served by the proxy. The row
-- keeps the file's name (<event id>-<12 hex of its SHA-256>.webp) and, as for
-- an imported cover, its public address in cover_image_url, so the read path
-- does not change. An uploaded picture is the organiser's own and carries no
-- licence credit; an imported one still needs all of it.
ALTER TABLE events ADD COLUMN cover_file text;
ALTER TABLE events ADD CONSTRAINT events_cover_file_shape
    CHECK (cover_file IS NULL OR cover_file ~ '^[0-9]+-[0-9a-f]{12}\.webp$');

ALTER TABLE events DROP CONSTRAINT events_cover_credit_complete;
ALTER TABLE events ADD CONSTRAINT events_cover_credit_complete CHECK (
    (cover_file IS NULL AND (
        (cover_image_url IS NULL AND cover_author IS NULL AND cover_license IS NULL
            AND cover_license_url IS NULL AND cover_source_url IS NULL)
        OR (cover_image_url IS NOT NULL AND cover_author IS NOT NULL AND cover_license IS NOT NULL
            AND cover_license_url IS NOT NULL AND cover_source_url IS NOT NULL)))
    OR (cover_file IS NOT NULL AND kind = 'OWN' AND cover_image_url IS NOT NULL
        AND cover_author IS NULL AND cover_license IS NULL
        AND cover_license_url IS NULL AND cover_source_url IS NULL)
);

-- Moderation may take the picture alone down; the owner is told why.
ALTER TABLE notice_outbox DROP CONSTRAINT notice_outbox_kind_known;
ALTER TABLE notice_outbox ADD CONSTRAINT notice_outbox_kind_known
    CHECK (kind IN ('EVENT_SUSPENDED', 'COMMUNITY_SUSPENDED', 'COMMUNITY_LOGO_REMOVED', 'EVENT_COVER_REMOVED'));
ALTER TABLE notice_outbox DROP CONSTRAINT notice_outbox_event_notice_names_event;
ALTER TABLE notice_outbox ADD CONSTRAINT notice_outbox_event_notice_names_event
    CHECK (kind NOT IN ('EVENT_SUSPENDED', 'EVENT_COVER_REMOVED') OR event_id IS NOT NULL);

ALTER TABLE audit_log DROP CONSTRAINT audit_log_action_known;
ALTER TABLE audit_log ADD CONSTRAINT audit_log_action_known CHECK (action IN (
    'EVENT_CREATED', 'EVENT_UPDATED', 'EVENT_CANCELLED', 'EVENT_DELETED',
    'EVENT_PUBLISHED', 'EVENT_SUSPENDED', 'EVENT_REINSTATED', 'REPORT_DISMISSED',
    'COVER_CHANGED', 'COVER_REMOVED'
));
