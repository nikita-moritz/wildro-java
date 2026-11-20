-- A suspension tells the owner why (DSA Art. 17). This service sends no mail:
-- it writes the decision to the outbox in the same transaction, and the event
-- service, which already carries the statements of reasons for events, queues
-- and delivers the notice.
ALTER TABLE outbox DROP CONSTRAINT outbox_type_known;
ALTER TABLE outbox ADD CONSTRAINT outbox_type_known CHECK (type IN ('community.closed', 'community.suspended'));
