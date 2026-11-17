-- What taking part costs, when the source says. Zero means free. Several prices
-- (adults, children, members) are kept in the description; this is the regular
-- adult price, the one a search engine shows as the offer. Amount and currency
-- come as a pair: both present or both absent.
ALTER TABLE events
    ADD COLUMN price_amount   numeric(8, 2),
    ADD COLUMN price_currency text,
    ADD CONSTRAINT events_price_complete CHECK ((price_amount IS NULL) = (price_currency IS NULL)),
    ADD CONSTRAINT events_price_not_negative CHECK (price_amount IS NULL OR price_amount >= 0),
    ADD CONSTRAINT events_price_currency_code CHECK (price_currency IS NULL OR price_currency ~ '^[A-Z]{3}$');
