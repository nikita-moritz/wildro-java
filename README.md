# wildro (Java)

An outdoor events directory for the region around Aachen: hikes, ranger tours,
trail runs and rides from national parks, clubs and public groups, in German,
English and Russian. Visitors read events on a list and a map; the card sends
them on to the organiser's own page.

This repository is the **Java implementation**: two Spring Boot services behind
a Next.js frontend. It ran production on wildro.app from 2026-10-20 and stays
live at **https://java.wildro.app**. The same specification was later
reimplemented in Go; both versions read the same database, so the two can be
compared request for request.

```
browser ─ Caddy ─> frontend (Next.js 16, App Router)
                     ├──> event-service      (Spring Boot 4, Java 25)  ──> PostgreSQL 17 + PostGIS
                     └──> analytics-service  (Spring Boot 4, Java 25)  ──> own database
```

Both services listen on loopback only. The public never reaches them: the
frontend renders on the server, caches by tag, and is the only thing exposed.

## What is where

| Path | What |
|---|---|
| `SPEC.md` | the behaviour specification (Russian). Sections marked `ПРОВЕРЕНО` are decided |
| `IMPLEMENTATION_PLAN.md` | the plan the services were built from |
| `services/event-service` | events: public reads (feed, map, event page) and the admin command line that writes them |
| `services/analytics-service` | cookieless page view counting and the stats behind the dashboard |
| `frontend` | the site: feed, map, event pages, legal pages, sitemap, revalidation endpoint |
| `docs/event-import.md` | the import format and the rules for writing an event |
| `docs/event-sources.md` | what may be published from which kind of source (copyright, GDPR) |
| `seed/dev-test-events.json` | seventeen invented test events for a development database |
| `scripts/` | deploy and admin wrappers as they run on the server |

Code comments cite `AGENTS.md`: those are the working notes of the private
repository. The decisions they refer to are summarised below.

## Decisions

**Microservices, one service per entity, each organised as vertical slices.**
Recorded against the recommendation of a modular monolith with the same
slices; stage one has a single entity, `Event`, which defers most of the cost.
Analytics is its own bounded context with its own database and knows nothing
about events.

**Traffic wins.** The site earns from search traffic, so no public page makes a
synchronous backend call in its hot path. Pages are cached server renders,
invalidated by tag: `event:<slug>`, `events:feed`, `events:map`. One write
busts all three shapes it can affect.

**Three read shapes over one entity.** `Event` owns the table; `EventShort`
(feed) and `EventPoint` (map) are projections, not entities, so there is one
owner of the data and one place for the schema to change.

**The only write path is a command line** on the server, the same jar as the
service started without a web server. It prints exactly one JSON object on
stdout (the caller is often a model), uses exit codes 0 ok, 1 refused, 2
usage, 3 failure, and checks every import in three layers: the published JSON
Schema (shape), the domain (rules across fields), the current state (a
cancelled event is not revived by an import). Audit entries are written in the
same transaction as the change they describe.

**Vocabularies are text behind a check constraint**, not PostgreSQL enums, so a
value can be retired without rewriting a type. The Java enum is the source of
truth, and a test reads the migrations and the import schema and fails if any
of the three drift apart.

**Links are https only, checked three times**: the import schema, the domain
type and a database constraint. The address ends up in an `href` on a public
page and is often written by a model from someone else's text.

**Analytics without cookies.** A visitor is a daily hash of salt, day, host,
address and user agent; bots are dropped, the rate is limited, rows older than
fourteen months are swept.

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
seconds, which is not RFC 3339.

## Run it

Needs JDK 25, Docker (tests run on `postgis/postgis:17-3.5` through
Testcontainers) and Bun for the frontend.

```
cd services/event-service && ./mvnw verify
cd services/analytics-service && ./mvnw verify
cd frontend && bun install && bun run build
```

The database needs the `postgis` and `pg_trgm` extensions created once by a
superuser; the migrations then run as an ordinary role.

Admin command line, against a deployed service:

```
scripts/events.sh dev import seed/dev-test-events.json --operator NAME          # dry run
scripts/events.sh dev import seed/dev-test-events.json --operator NAME --write
scripts/events.sh dev list --all
```

CI (`.github/workflows/ci.yml`) builds and tests both services against a real
PostGIS and typechecks, lints and builds the frontend.
