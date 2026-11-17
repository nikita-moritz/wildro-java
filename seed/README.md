# Dev test events

`dev-test-events.json` fills the demo database behind wildro.nikone.dev with
seventeen events so the read API and the frontend can be built and checked
against something realistic. **Never import it anywhere but dev.**

They are test artifacts and marked as such, so they can be removed in one go:

- every slug starts with `test-`;
- every organiser is invented;
- every source link points at `example.org`, the domain reserved for examples;
- the audit log names the operator `claude-test-seed`.

The places and coordinates are real, because the map cannot be checked
otherwise. The set covers the cases a list and a card have to get right: two
past events, one cancelled (cancelled by command after the import, since an
import never cancels), one draft, one spanning two days, one recurring with no
date in its slug, events in Belgium and the Netherlands for country names in
three languages, and one on 25 October 2026, the day summer time ends, where
the offset changes from `+02:00` to `+01:00`.

```sh
scripts/events.sh dev import seed/dev-test-events.json --operator claude-test-seed           # dry run
scripts/events.sh dev import seed/dev-test-events.json --operator claude-test-seed --write
scripts/events.sh dev cancel test-abgesagt-venn-sonnenaufgang --operator claude-test-seed \
  --confirm test-abgesagt-venn-sonnenaufgang
```

Importing again is safe: every event comes back `UNCHANGED`.
