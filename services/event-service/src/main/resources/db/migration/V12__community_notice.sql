-- The community service suspends groups but sends no mail; its outbox entry
-- community.suspended becomes a notice here, delivered by the same relay as the
-- event notices (DSA Art. 17). Such a notice names no event: the group's name
-- and language travel in the payload.
ALTER TABLE notice_outbox DROP CONSTRAINT notice_outbox_kind_known;
ALTER TABLE notice_outbox ADD CONSTRAINT notice_outbox_kind_known
    CHECK (kind IN ('EVENT_SUSPENDED', 'COMMUNITY_SUSPENDED'));
ALTER TABLE notice_outbox ADD CONSTRAINT notice_outbox_event_notice_names_event
    CHECK (kind <> 'EVENT_SUSPENDED' OR event_id IS NOT NULL);
