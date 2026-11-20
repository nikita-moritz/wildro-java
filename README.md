# wildro (Java)

An outdoor events directory for the region around Aachen: hikes, ranger tours,
trail runs and rides from national parks, clubs and public groups, in German,
English and Russian. Visitors find events in a list and on a map; signed-in
people publish their own events and run groups together.

**Live: https://java.wildro.app**

This is the **Java implementation**: four Spring Boot services behind a Next.js
frontend, sign-in delegated to elvix. It ran production on wildro.app from
2026-10-20; the same specification was later reimplemented in Go, which now
carries the working site. This version stays live as the complete Java
implementation, on its own copy of the events.

```
                         elvix (sign-in, sessions, platform roles, webhooks)
                           ▲
browser ── Caddy ──> frontend (Next.js 16, App Router, server rendered, cached by tag)
                       │  session cookie ─> 10-minute elvix JWT, passed on per request
                       ├──> event-service      events, own events, reports, moderation ─> PostgreSQL 17 + PostGIS
                       ├──> user-service       account, terms, elvix webhooks           ─> own database
                       ├──> community-service  groups, memberships, roles                ─> own database
                       └──> analytics-service  cookieless page views                     ─> own database

  between services:  synchronous check on write (the caller's own JWT)
                     asynchronous consequences through transactional outboxes, pulled by the consumer
```

Every service listens on loopback only. The public reaches the frontend and
nothing else; the frontend's server is the only caller of the services.

## What it does

| | |
|---|---|
| **Read** | feed with filters and search, map by viewport, event pages; all public pages are cached server renders, no backend call in the hot path |
| **Sign in** | Google, passkey or email code through elvix; wildro stores no password, code or session |
| **Own events** | written in one language, shown in all three with a note; drafts, publishing, cancelling; at most 5 published a day and 30 upcoming per account |
| **Groups** | one owner, admins, members; free joining; names visible to members only; ownership handed to an admin; closing cancels the group's future events |
| **Reports** | anyone, guests included, five an hour per address; moderators take events down with a reason, the owner gets the statement of reasons by email (DSA Art. 16 and 17) |
| **Admin** | a command line on the server imports curated events from public sources, checked in three layers |

## What is where

| Path | What |
|---|---|
| `services/event-service` | events: public reads, own events, reports, moderation, the notice relay, the admin command line |
| `services/user-service` | the account: profile, terms acceptance, elvix webhooks (block, deletion), outbox `user.erased` |
| `services/community-service` | groups, memberships and roles on the membership, outbox `community.closed` |
| `services/analytics-service` | cookieless page view counting and the stats behind the owner's dashboard |
| `frontend` | the site, and the backend-for-frontend routes that hold the session and talk to the services |
| `SPEC.md` | the behaviour specification (Russian). Sections marked `ПРОВЕРЕНО` are decided; §18 is stage two |
| `docs/event-import.md`, `docs/event-sources.md` | the import format, and what may be published from which kind of source |
| `scripts/` | deploy and admin wrappers as they run on the server |

Code comments cite `AGENTS.md`: the working notes of the private repository.
The decisions they refer to are summarised here.

## Decisions

**One service per bounded context, vertical slices inside.** Chosen over the
recommended modular monolith; the reasoning is kept in the private notes so it
is not re-argued. Each service owns its database and is reached only through
its API. No service reads another's tables.

**Traffic wins.** The site earns from search, so public pages never wait on a
backend: they are cached renders invalidated by tag (`event:<slug>`,
`events:feed`, `community:<slug>`). Nothing personal is ever part of such a
render. Where a public page needs to know who is looking (join a group,
report an event), the browser asks afterwards, and the SDK of the sign-in
provider loads on the account pages only.

**Sign-in is elvix's, the account is ours** (SPEC §18.1). The browser holds
elvix's session token in an httpOnly cookie; the frontend's server trades it
for a ten-minute JWT on each request that needs the person. Every service is a
plain OAuth2 resource server: discovery from the issuer, our clientId as the
audience, roles from the token's `roles` claim. The subject of a verified
token is the only way a request names a person. Email and name are read from
elvix by that subject, never taken from the caller.

**Checks on write are synchronous, consequences asynchronous** (SPEC §18.4).
Before an event is written in a group's name, the event service asks the
community service, with the caller's own token, whether they are owner or
admin *now*; before anyone publishes, the user service is asked whether they
accepted the terms in force. No service-to-service credential vouches for a
person. What one module's decision means for another goes through a
**transactional outbox**: written in the same transaction as the decision,
pulled by the consumer from `/internal/outbox` with a shared internal token,
and applied with the consumer's cursor moved in the same transaction as the
effect. A module that is down delays the consequence; it is never lost and
never applied twice.

| Outbox entry | Producer | What the consumers do |
|---|---|---|
| `user.erased` | user-service, from elvix's deletion webhook | community-service: memberships go; an owned group passes to the longest-serving admin or closes. event-service: the person's future personal events are cancelled, their name leaves past ones |
| `community.closed` | community-service | event-service: the group's future events are cancelled; pages stay at their addresses, marked cancelled |
| `community.suspended` | community-service, on a moderator's decision | event-service: the owner's statement of reasons joins the queue of event notices and goes out by email in the group's language; the community service sends no mail itself |

**Webhooks are verified on the raw body.** elvix signs `"<t>.<body>"` with
HMAC-SHA256; the frontend forwards the bytes unchanged, the user service
checks the signature in constant time with a five-minute replay window,
applies each event once by id and orders status changes by elvix's own
timestamp, so a late delivery never overwrites a newer state.

**Own events reuse the external event's rules.** One `Event` entity, one set
of domain checks for an import and for a person. An own event is written in
one language; the text is stored under every locale with `original_locale`,
so the read path keeps exactly one row per event and language, and the page
says "written in the organiser's language". The only states are draft,
published, cancelled and suspended; a cancelled event is never revived, a
published one never goes back to draft.

**Moderation leaves a trail and tells the owner.** A suspension closes the
event's open reports and queues the statement of reasons in the same
transaction. A relay sends it later through Resend, in the event's language,
retrying with a growing pause; the recipient's address is looked up at send
time and never stored by the event service. Every privileged action is in the
audit log of the service that performed it, written in the same transaction.

**Vocabularies are text behind check constraints**, not PostgreSQL enums, so a
value can be retired without rewriting a type. The Java enum is the source of
truth; a test reads the migrations and the import schema and fails when any
of the copies drift.

**Links are https only, checked three times**: request or import schema, domain
type, database constraint. They end up in an `href` on a public page.

**Analytics without cookies.** A visitor is a daily hash of salt, day, host,
address and user agent; bots are dropped and rows expire after fourteen months.

## Traps designed around

`Specification` with PostGIS, pagination and a translations table:

- **Geography is not expressible in plain Criteria.** Radius and
  nearest-neighbour predicates come from `hibernate-spatial`, registered as
  functions and called from the criteria builder.
- **Ordering by distance cannot come from `Sort`**, which takes property names,
  not expressions; the ordering lives inside the specification.
- **`Page` runs a count query on every request.** Public lists use `Slice`.
- **`OFFSET` degrades with depth**, so list depth is bounded, which is also a
  search requirement: unbounded depth mints unbounded URLs.
- **Joining translations duplicates rows.** A locale predicate keeps one row per
  event, so page boundaries stay correct.

Spring Boot 4 specifics paid for once: Jackson 3 lives in `tools.jackson`;
`json-schema-validator` 3.x treats `format` as an annotation under draft
2020-12 unless format assertions are switched on, so `"next Saturday"` would
pass as a date; `src/test/resources/application.properties` replaces the main
file instead of merging with it; `OffsetDateTime.toString()` drops zero
seconds, which is not RFC 3339; a resource server configured with an empty
audience accepts every token of the issuer, so the event service builds its
decoder itself and refuses all tokens while no clientId is set.

Next.js 16 with Cache Components: a page that reads configuration at build
time is prerendered with whatever the build machine had, so pages that need
runtime settings call `connection()` first; a redirect built on `request.url`
behind a reverse proxy points at the proxy's upstream, so every redirect is
built on the configured site URL.

## Tests

Around 230 tests, all against a real `postgis/postgis:17-3.5` through
Testcontainers, none against an in-memory database. They are written as
rules rather than methods: *a late webhook does not overwrite a newer status*,
*yesterday's admin is not today's*, *a deleted owner's group passes to the
longest-serving admin*, *a failing mail transport is retried, not lost*. Tokens
come from Spring Security's test support; the other services are replaced by
in-memory fakes at their interface.

## Run it

Needs JDK 25, Docker and Bun.

```
cd services/event-service && ./mvnw verify
cd services/user-service && ./mvnw verify
cd services/community-service && ./mvnw verify
cd services/analytics-service && ./mvnw verify
cd frontend && bun install && bun run build
```

The event database needs the `postgis` and `pg_trgm` extensions created once by
a superuser; the migrations then run as an ordinary role. Each service reads
its datasource, elvix clientId and the internal token from the environment and
refuses to start, or refuses every token, without them.

Admin command line, against a deployed service:

```
scripts/events.sh java list --all
scripts/events.sh java import events.json --operator NAME          # dry run
scripts/events.sh java import events.json --operator NAME --write
```

CI (`.github/workflows/ci.yml`) builds and tests every service against a real
PostGIS and typechecks, lints and builds the frontend. Deploys are run by hand
from the server: a public repository never routes a stranger's pull request
to the machine that serves the site.
