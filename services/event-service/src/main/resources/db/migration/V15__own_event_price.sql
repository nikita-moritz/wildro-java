-- An own event is free unless its organiser names a price (SPEC.md §18.2,
-- owner 2026-11-23), so the "free" filter finds it. External events keep
-- "absent when the source says nothing" (V6).
UPDATE events
SET price_amount = 0, price_currency = 'EUR'
WHERE kind = 'OWN' AND price_amount IS NULL;

ALTER TABLE events
    ADD CONSTRAINT events_own_has_price CHECK (kind <> 'OWN' OR price_amount IS NOT NULL);
