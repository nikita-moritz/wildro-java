-- Reports on a community (DSA Art. 16), the same shape as the event service's
-- reports on events: open to guests, closed by a decision. The reporter's
-- address is never stored; the rate limit keeps it in memory only.
CREATE TABLE community_reports (
    id           bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    community_id bigint      NOT NULL REFERENCES communities (id) ON DELETE CASCADE,
    reason       text        NOT NULL,
    details      text        NOT NULL,
    status       text        NOT NULL DEFAULT 'OPEN',
    created_at   timestamptz NOT NULL DEFAULT now(),
    resolved_at  timestamptz,
    resolved_by  text,

    CONSTRAINT community_reports_reason_known CHECK (reason IN (
        'SPAM', 'MISLEADING', 'DANGEROUS', 'ILLEGAL', 'PERSONAL_DATA', 'OTHER'
    )),
    CONSTRAINT community_reports_status_known CHECK (status IN ('OPEN', 'ACTIONED', 'DISMISSED')),
    CONSTRAINT community_reports_details_length CHECK (char_length(details) BETWEEN 1 AND 2000),
    CONSTRAINT community_reports_resolution_pair CHECK ((status = 'OPEN') = (resolved_at IS NULL))
);

CREATE INDEX community_reports_open_idx ON community_reports (community_id, created_at) WHERE status = 'OPEN';

ALTER TABLE audit_log DROP CONSTRAINT audit_log_action_known;
ALTER TABLE audit_log ADD CONSTRAINT audit_log_action_known CHECK (action IN (
    'COMMUNITY_CREATED', 'COMMUNITY_UPDATED', 'COMMUNITY_CLOSED', 'COMMUNITY_SUSPENDED',
    'COMMUNITY_REINSTATED', 'ROLE_CHANGED', 'MEMBER_REMOVED', 'OWNERSHIP_TRANSFERRED',
    'REPORT_DISMISSED'
));
