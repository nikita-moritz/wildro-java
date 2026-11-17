package app.wildro.event.admin;

import app.wildro.event.TestcontainersConfiguration;
import app.wildro.event.admin.ImportOutcome.Action;
import app.wildro.event.admin.ImportOutcome.Mode;
import app.wildro.event.audit.AuditAction;
import app.wildro.event.audit.AuditEntry;
import app.wildro.event.audit.AuditLog;
import app.wildro.event.audit.Operator;
import app.wildro.event.domain.Event;
import app.wildro.event.domain.EventRepository;
import app.wildro.event.domain.EventStatus;
import app.wildro.event.domain.LinkNetwork;
import app.wildro.event.read.EventPage;
import app.wildro.event.read.EventReader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

import static app.wildro.event.admin.ImportDocuments.batch;
import static app.wildro.event.admin.ImportDocuments.first;
import static app.wildro.event.admin.ImportDocuments.single;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The import against a real database. Each test is one requirement of SPEC.md
 * §0.1, named as the rule rather than as the mechanism.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class EventImporterTest {

	private static final Operator NIKITA = Operator.cli("nikita");

	@Autowired
	private EventImporter importer;

	@Autowired
	private EventCanceller canceller;

	@Autowired
	private EventRepository events;

	@Autowired
	private EventReader reader;

	@Autowired
	private AuditLog audit;

	private List<AuditEntry> auditOf(String slug) {
		return this.audit.findByEntityTypeAndEntityKeyOrderByOccurredAtAscIdAsc("EVENT", slug);
	}

	private static List<String> pointers(ImportOutcome outcome) {
		return outcome.violations().stream().map(Violation::pointer).toList();
	}

	@Test
	void aDryRunPlansButWritesNothing() {
		ImportOutcome outcome = this.importer.plan(single("dry-run"));

		assertThat(outcome.ok()).isTrue();
		assertThat(outcome.mode()).isEqualTo(Mode.DRY_RUN);
		assertThat(outcome.items()).singleElement().extracting(ImportOutcome.Item::action).isEqualTo(Action.CREATE);
		assertThat(this.events.findBySlug("dry-run")).isEmpty();
		assertThat(auditOf("dry-run")).isEmpty();
	}

	@Test
	void writingCreatesTheEventAndAuditsWhoDidIt() {
		ImportOutcome outcome = this.importer.apply(single("created"), NIKITA);

		assertThat(outcome.ok()).isTrue();
		Event event = this.events.findBySlug("created").orElseThrow();
		assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
		assertThat(event.translation("ru").getPlaceName()).isEqualTo("Ахен, Германия");

		assertThat(auditOf("created")).singleElement().satisfies(entry -> {
			assertThat(entry.getAction()).isEqualTo(AuditAction.EVENT_CREATED);
			assertThat(entry.getOperator()).isEqualTo("nikita");
			assertThat(entry.getOsUser()).isNotBlank();
			assertThat(entry.getEntityId()).isEqualTo(event.getId());
		});
	}

	/** SPEC.md §0.1: a repeated run never creates a duplicate. */
	@Test
	void importingTheSameDocumentAgainChangesNothing() {
		ObjectNode document = single("idempotent");
		this.importer.apply(document, NIKITA);

		ImportOutcome second = this.importer.apply(document, NIKITA);

		assertThat(second.items()).singleElement().extracting(ImportOutcome.Item::action).isEqualTo(Action.UNCHANGED);
		assertThat(this.events.findAll()).filteredOn(e -> e.getSlug().equals("idempotent")).hasSize(1);
		assertThat(auditOf("idempotent")).hasSize(1);
	}

	@Test
	void aRepeatedImportUpdatesAndNamesExactlyWhatChanged() {
		ObjectNode document = single("revised");
		this.importer.apply(document, NIKITA);

		first(document).put("distanceKm", 21.0);
		((ObjectNode) first(document).at("/translations/en")).put("name", "Eifelsteig, stage one");
		ImportOutcome outcome = this.importer.apply(document, NIKITA);

		assertThat(outcome.items()).singleElement().satisfies(item -> {
			assertThat(item.action()).isEqualTo(Action.UPDATE);
			assertThat(item.changedFields()).containsExactly("distanceKm", "translations/en");
		});
		assertThat(this.events.findBySlug("revised").orElseThrow().translation("en").getName())
				.isEqualTo("Eifelsteig, stage one");
		assertThat(auditOf("revised")).extracting(AuditEntry::getAction)
				.containsExactly(AuditAction.EVENT_CREATED, AuditAction.EVENT_UPDATED);
		assertThat(auditOf("revised").getLast().getDetails()).contains("distanceKm", "translations/en");
	}

	/** 18.5 written as 18.50 is the same distance, not a change. */
	@Test
	void equivalentNumbersAreNotAChange() {
		ObjectNode document = single("same-number");
		this.importer.apply(document, NIKITA);

		first(document).put("distanceKm", new java.math.BigDecimal("18.50"));

		assertThat(this.importer.plan(document).items()).singleElement()
				.extracting(ImportOutcome.Item::action).isEqualTo(Action.UNCHANGED);
	}

	/** SPEC.md §0.1: unknown keys are rejected, not ignored. A typo must not mean lost data. */
	@Test
	void aMistypedFieldIsRefusedWithItsPath() {
		ObjectNode document = single("typo");
		first(document).remove("distanceKm");
		first(document).put("distanceKmm", 18.5);

		ImportOutcome outcome = this.importer.apply(document, NIKITA);

		assertThat(outcome.ok()).isFalse();
		assertThat(pointers(outcome)).containsExactly("/events/0/distanceKmm");
		assertThat(this.events.findBySlug("typo")).isEmpty();
	}

	/**
	 * Open question 2: an external event carries no capacity. The schema has no
	 * such field, so a number taken from someone's chat cannot even be written.
	 */
	@Test
	void anExternalEventCannotCarryACapacity() {
		ObjectNode document = single("capacity");
		first(document).put("maxParticipants", 12);

		assertThat(pointers(this.importer.plan(document))).containsExactly("/events/0/maxParticipants");
	}

	@Test
	void aMissingStartPointIsRefused() {
		ObjectNode document = single("no-point");
		first(document).remove("startPoint");

		assertThat(pointers(this.importer.plan(document))).containsExactly("/events/0/startPoint");
	}

	@Test
	void anUnknownActivityTypeIsRefused() {
		ObjectNode document = single("paragliding");
		first(document).put("activityType", "PARAGLIDING");

		assertThat(pointers(this.importer.plan(document))).containsExactly("/events/0/activityType");
	}

	/** A time without an offset would let the server's zone decide what it means. */
	@Test
	void aTimeWithoutAnOffsetIsRefused() {
		ObjectNode document = single("no-offset");
		first(document).put("startsAt", "2026-10-03T09:00:00");

		assertThat(pointers(this.importer.plan(document))).contains("/events/0/startsAt");
	}

	/** Draft 2020-12 makes "format" an annotation by default; here it is an assertion. */
	@Test
	void somethingThatIsNotADateIsRefused() {
		ObjectNode document = single("not-a-date");
		first(document).put("startsAt", "next Saturday+02:00");

		assertThat(pointers(this.importer.plan(document))).contains("/events/0/startsAt");
	}

	@Test
	void aBlankDescriptionIsRefused() {
		ObjectNode document = single("blank");
		((ObjectNode) first(document).at("/translations/ru")).put("description", "   ");

		assertThat(pointers(this.importer.plan(document))).contains("/events/0/translations/ru/description");
	}

	/** A rule across two fields, which the schema cannot express and the domain does. */
	@Test
	void anEndBeforeTheStartIsRefusedByTheDomain() {
		ObjectNode document = single("backwards");
		String startsAt = first(document).get("startsAt").asString();
		first(document).put("endsAt", ImportDocuments.rfc3339(java.time.OffsetDateTime.parse(startsAt).minusHours(1)));

		ImportOutcome outcome = this.importer.plan(document);

		assertThat(outcome.violations()).singleElement().satisfies(violation -> {
			assertThat(violation.pointer()).isEqualTo("/events/0/endsAt");
			assertThat(violation.reason()).contains("after startsAt");
		});
	}

	/** SPEC.md §0.1: a batch applies wholly or not at all. */
	@Test
	void oneBadEventStopsTheWholeBatch() {
		ObjectNode document = batch("batch-good", "batch-bad");
		((ObjectNode) document.at("/events/1")).put("activityType", "PARAGLIDING");

		ImportOutcome outcome = this.importer.apply(document, NIKITA);

		assertThat(pointers(outcome)).containsExactly("/events/1/activityType");
		assertThat(this.events.findBySlug("batch-good")).isEmpty();
		assertThat(auditOf("batch-good")).isEmpty();
	}

	@Test
	void theSameSlugTwiceInOneDocumentIsRefused() {
		ImportOutcome outcome = this.importer.plan(batch("twice", "twice"));

		assertThat(pointers(outcome)).containsExactly("/events/1/slug");
	}

	@Test
	void everyViolationIsReportedAtOnce() {
		ObjectNode document = batch("many-a", "many-b");
		((ObjectNode) document.at("/events/0")).put("activityType", "PARAGLIDING");
		((ObjectNode) document.at("/events/1")).remove("organizer");

		assertThat(pointers(this.importer.plan(document)))
				.containsExactlyInAnyOrder("/events/0/activityType", "/events/1/organizer");
	}

	/** A cancellation is a deliberate act and is never undone by importing the same event again. */
	@Test
	void importingACancelledEventDoesNotReviveIt() {
		ObjectNode document = single("cancelled");
		this.importer.apply(document, NIKITA);
		this.canceller.cancel("cancelled", "cancelled", NIKITA);

		ImportOutcome outcome = this.importer.apply(document, NIKITA);

		assertThat(pointers(outcome)).containsExactly("/events/0/status");
		assertThat(this.events.findBySlug("cancelled").orElseThrow().getStatus()).isEqualTo(EventStatus.CANCELLED);
	}

	/** Taking a public page off the site is its own decision (SPEC.md §0.3), not a side effect of "DRAFT". */
	@Test
	void aPublishedEventCannotBeSentBackToDraft() {
		ObjectNode document = single("unpublish");
		this.importer.apply(document, NIKITA);

		first(document).put("status", "DRAFT");

		assertThat(pointers(this.importer.apply(document, NIKITA))).containsExactly("/events/0/status");
		assertThat(this.events.findBySlug("unpublish").orElseThrow().getStatus()).isEqualTo(EventStatus.PUBLISHED);
	}

	@Test
	void aDraftCanBePublishedByImportingItAgain() {
		ObjectNode document = single("draft-first");
		first(document).put("status", "DRAFT");
		this.importer.apply(document, NIKITA);

		first(document).put("status", "PUBLISHED");
		ImportOutcome outcome = this.importer.apply(document, NIKITA);

		assertThat(outcome.items()).singleElement()
				.satisfies(item -> assertThat(item.changedFields()).containsExactly("status"));
		assertThat(this.events.findBySlug("draft-first").orElseThrow().getStatus()).isEqualTo(EventStatus.PUBLISHED);
	}

	@Test
	void linksAreStoredPerScope() {
		ObjectNode document = single("with-links");
		ObjectNode links = first(document).putObject("links");
		links.putArray("event").add("https://www.facebook.com/events/1");
		links.putArray("community").add("https://t.me/gruppe").add("https://www.instagram.com/gruppe/");

		assertThat(this.importer.apply(document, NIKITA).ok()).isTrue();

		assertThat(this.reader.page("with-links", "de").orElseThrow()).satisfies(page -> {
			assertThat(page.eventLinks())
					.containsExactly(new EventPage.Link(LinkNetwork.FACEBOOK, "https://www.facebook.com/events/1"));
			assertThat(page.communityLinks()).containsExactly(
					new EventPage.Link(LinkNetwork.TELEGRAM, "https://t.me/gruppe"),
					new EventPage.Link(LinkNetwork.INSTAGRAM, "https://www.instagram.com/gruppe/"));
		});
	}

	/** Adding, reordering and removing links are each a change, named by scope; repeating is not. */
	@Test
	void aRepeatedImportSeesWhichLinksChanged() {
		ObjectNode document = single("links-revised");
		ObjectNode links = first(document).putObject("links");
		links.putArray("event").add("https://t.me/gruppe/42");
		links.putArray("community").add("https://t.me/gruppe").add("https://wa.me/491700000000");
		this.importer.apply(document, NIKITA);

		assertThat(this.importer.plan(document).items()).singleElement()
				.extracting(ImportOutcome.Item::action).isEqualTo(Action.UNCHANGED);

		links.putArray("community").add("https://wa.me/491700000000").add("https://t.me/gruppe");
		ImportOutcome reordered = this.importer.apply(document, NIKITA);
		assertThat(reordered.items()).singleElement()
				.extracting(ImportOutcome.Item::changedFields).isEqualTo(List.of("links/community"));

		first(document).remove("links");
		ImportOutcome removed = this.importer.apply(document, NIKITA);
		assertThat(removed.items()).singleElement()
				.extracting(ImportOutcome.Item::changedFields).isEqualTo(List.of("links/event", "links/community"));
		assertThat(this.reader.page("links-revised", "de").orElseThrow().communityLinks()).isEmpty();
	}

	@Test
	void aLinkThatIsNotHttpsIsRefusedWithItsPath() {
		ObjectNode document = single("bad-link");
		first(document).putObject("links").putArray("community").add("https://t.me/gruppe").add("http://t.me/gruppe");

		ImportOutcome outcome = this.importer.plan(document);

		assertThat(outcome.ok()).isFalse();
		assertThat(pointers(outcome)).containsExactly("/events/0/links/community/1");
	}

	@Test
	void anUnknownKindOfLinkIsRefused() {
		ObjectNode document = single("unknown-scope");
		first(document).putObject("links").putArray("organizer").add("https://t.me/gruppe");

		assertThat(pointers(this.importer.plan(document))).containsExactly("/events/0/links/organizer");
	}

	@Test
	void aCoverAndItsCreditAreStoredAndShown() {
		ObjectNode document = single("with-cover");
		first(document).put("coverImageUrl", "https://media.wildro.app/covers/events/with-cover-v1.webp");
		ObjectNode credit = first(document).putObject("coverCredit");
		credit.put("author", "Jane Doe").put("license", "CC BY-SA 4.0")
				.put("licenseUrl", "https://creativecommons.org/licenses/by-sa/4.0/")
				.put("sourceUrl", "https://commons.wikimedia.org/wiki/File:Example.jpg");

		assertThat(this.importer.apply(document, NIKITA).ok()).isTrue();

		assertThat(this.reader.page("with-cover", "de").orElseThrow().coverCredit())
				.isEqualTo(new EventPage.Credit("Jane Doe", "CC BY-SA 4.0",
						"https://creativecommons.org/licenses/by-sa/4.0/", "https://commons.wikimedia.org/wiki/File:Example.jpg"));
	}

	@Test
	void aCoverWithoutCreditIsRefusedAtTheDoor() {
		ObjectNode document = single("cover-no-credit");
		first(document).put("coverImageUrl", "https://media.wildro.app/covers/events/x.webp");

		ImportOutcome outcome = this.importer.plan(document);

		assertThat(outcome.ok()).isFalse();
		assertThat(pointers(outcome)).isNotEmpty();
	}

}
