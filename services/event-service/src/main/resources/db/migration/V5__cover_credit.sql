-- Who made the cover photo and under which licence. An event's cover is never
-- ours by default: it is a photo from a free archive such as Wikimedia Commons,
-- shown under a licence that requires naming the author and the licence. Without
-- these four the picture may not be shown at all, so they come as a set: all
-- present with a cover, all absent without one.
ALTER TABLE events
    ADD COLUMN cover_author      text,
    ADD COLUMN cover_license     text,
    ADD COLUMN cover_license_url text,
    ADD COLUMN cover_source_url  text,
    ADD CONSTRAINT events_cover_credit_complete CHECK (
        (cover_image_url IS NULL AND cover_author IS NULL AND cover_license IS NULL
            AND cover_license_url IS NULL AND cover_source_url IS NULL)
        OR (cover_image_url IS NOT NULL AND cover_author IS NOT NULL AND cover_license IS NOT NULL
            AND cover_license_url IS NOT NULL AND cover_source_url IS NOT NULL)
    ),
    ADD CONSTRAINT events_cover_urls_https CHECK (
        (cover_license_url IS NULL OR cover_license_url ~ '^https://')
        AND (cover_source_url IS NULL OR cover_source_url ~ '^https://')
    );
