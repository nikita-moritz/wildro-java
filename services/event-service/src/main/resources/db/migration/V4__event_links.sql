-- Optional links that go with an event, beside the source link. Two kinds, and
-- the difference matters to a visitor: a link to the event itself (its post, its
-- chat, its page on a network) is where this one outing is discussed, a link to
-- the community is where the people who run it can be found the rest of the year.
--
-- A table rather than columns per network: which networks exist is not a schema
-- decision, and an organiser may have two chats or none. The network is not
-- stored at all. It is read off the address by LinkNetwork, so the two can never
-- disagree.
CREATE TABLE event_links (
    event_id bigint  NOT NULL REFERENCES events (id) ON DELETE CASCADE,

    -- The order the author wrote them in, event links first. It is the order the
    -- page shows, and with event_id it is the key Hibernate updates a list by.
    position integer NOT NULL,

    scope    text    NOT NULL,
    url      text    NOT NULL,

    PRIMARY KEY (event_id, position),
    CONSTRAINT event_links_scope_known CHECK (scope IN ('EVENT', 'COMMUNITY')),

    -- The page renders these as links a visitor clicks. Anything but https would
    -- let a document put a javascript: or data: address on a public page.
    CONSTRAINT event_links_https CHECK (url ~ '^https://[^/[:space:]]+'),
    CONSTRAINT event_links_position_not_negative CHECK (position >= 0)
);
