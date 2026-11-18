-- Delivery of the statements of reasons queued by V9 (DSA Art. 17), by email.
-- A failed send is retried with a growing pause; a notice whose recipient no
-- longer exists, or that failed too often, is abandoned with the reason kept.
ALTER TABLE notice_outbox ADD COLUMN attempts        integer     NOT NULL DEFAULT 0;
ALTER TABLE notice_outbox ADD COLUMN next_attempt_at timestamptz;
ALTER TABLE notice_outbox ADD COLUMN last_error      text;
ALTER TABLE notice_outbox ADD COLUMN abandoned_at    timestamptz;

DROP INDEX notice_outbox_unsent;
CREATE INDEX notice_outbox_pending ON notice_outbox (id) WHERE sent_at IS NULL AND abandoned_at IS NULL;
