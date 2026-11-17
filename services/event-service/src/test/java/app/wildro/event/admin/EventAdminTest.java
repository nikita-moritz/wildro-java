package app.wildro.event.admin;

import app.wildro.event.TestcontainersConfiguration;
import app.wildro.event.audit.AuditAction;
import app.wildro.event.audit.AuditEntry;
import app.wildro.event.audit.AuditLog;
import app.wildro.event.audit.Operator;
import app.wildro.event.domain.EventRepository;
import app.wildro.event.domain.EventStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Slice;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.node.ObjectNode;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static app.wildro.event.admin.ImportDocuments.first;
import static app.wildro.event.admin.ImportDocuments.single;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cancelling, deleting and listing: the admin actions beside the import. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class EventAdminTest {

	private static final Operator NIKITA = Operator.cli("nikita");

	@Autowired
	private EventImporter importer;

	@Autowired
	private EventCanceller canceller;

	@Autowired
	private EventDeleter deleter;

	@Autowired
	private EventLister lister;

	@Autowired
	private EventRepository events;

	@Autowired
	private AuditLog audit;

	private void create(String slug) {
		assertThat(this.importer.apply(single(slug), NIKITA).ok()).isTrue();
	}

	private void create(ObjectNode document) {
		assertThat(this.importer.apply(document, NIKITA).ok()).isTrue();
	}

	private List<AuditEntry> auditOf(String slug) {
		return this.audit.findByEntityTypeAndEntityKeyOrderByOccurredAtAscIdAsc("EVENT", slug);
	}

	private static AdminRefusal.Code codeOf(Throwable thrown) {
		return ((AdminRefusal) thrown).getCode();
	}

	@Test
	void cancellingNeedsTheSlugRepeated() {
		create("confirm-me");

		assertThatThrownBy(() -> this.canceller.cancel("confirm-me", "yes", NIKITA))
				.isInstanceOf(AdminRefusal.class)
				.satisfies(thrown -> assertThat(codeOf(thrown)).isEqualTo(AdminRefusal.Code.CONFIRMATION_MISMATCH));
		assertThat(this.events.findBySlug("confirm-me").orElseThrow().getStatus()).isEqualTo(EventStatus.PUBLISHED);
	}

	@Test
	void cancellingMarksTheEventAndAuditsItOnce() {
		create("to-cancel");

		assertThat(this.canceller.cancel("to-cancel", "to-cancel", NIKITA)).isTrue();
		assertThat(this.canceller.cancel("to-cancel", "to-cancel", NIKITA)).isFalse();

		assertThat(this.events.findBySlug("to-cancel").orElseThrow().getStatus()).isEqualTo(EventStatus.CANCELLED);
		assertThat(auditOf("to-cancel")).extracting(AuditEntry::getAction)
				.containsExactly(AuditAction.EVENT_CREATED, AuditAction.EVENT_CANCELLED);
		assertThat(auditOf("to-cancel").getLast().getDetails()).contains("PUBLISHED");
	}

	@Test
	void cancellingSomethingThatIsNotThereIsRefused() {
		assertThatThrownBy(() -> this.canceller.cancel("ghost", "ghost", NIKITA))
				.satisfies(thrown -> assertThat(codeOf(thrown)).isEqualTo(AdminRefusal.Code.NOT_FOUND));
	}

	/** Removing an address is a decision with a reason (SPEC.md §0.3). */
	@Test
	void deletingNeedsAReason() {
		create("keep-me");

		assertThatThrownBy(() -> this.deleter.delete("keep-me", "keep-me", "  ", NIKITA))
				.satisfies(thrown -> assertThat(codeOf(thrown)).isEqualTo(AdminRefusal.Code.REASON_REQUIRED));
		assertThat(this.events.findBySlug("keep-me")).isPresent();
	}

	@Test
	void theAuditOfADeletionOutlivesTheEvent() {
		create("junk");
		Long id = this.events.findBySlug("junk").orElseThrow().getId();

		this.deleter.delete("junk", "junk", "imported twice under two slugs", NIKITA);
		this.events.flush();

		assertThat(this.events.findBySlug("junk")).isEmpty();
		assertThat(auditOf("junk").getLast()).satisfies(entry -> {
			assertThat(entry.getAction()).isEqualTo(AuditAction.EVENT_DELETED);
			assertThat(entry.getEntityId()).isEqualTo(id);
			assertThat(entry.getDetails()).contains("imported twice under two slugs");
		});
	}

	/** Open question 4: by default what has not passed yet, drafts included; --all adds the rest. */
	@Test
	void theListShowsWhatIsStillAheadUnlessAskedForEverything() {
		create("ahead");

		ObjectNode draft = single("ahead-draft");
		first(draft).put("status", "DRAFT");
		create(draft);

		ObjectNode past = single("behind");
		OffsetDateTime lastMonth = OffsetDateTime.now(ZoneOffset.ofHours(2)).minusMonths(1);
		first(past).put("startsAt", ImportDocuments.rfc3339(lastMonth));
		((ObjectNode) first(past).get("source")).put("readAt", ImportDocuments.rfc3339(lastMonth.minusDays(3)));
		create(past);

		create("called-off");
		this.canceller.cancel("called-off", "called-off", NIKITA);

		assertThat(slugs(this.lister.list(false, 0, 50))).containsExactlyInAnyOrder("ahead", "ahead-draft");
		assertThat(slugs(this.lister.list(true, 0, 50)))
				.containsExactlyInAnyOrder("ahead", "ahead-draft", "behind", "called-off");
	}

	/** Every list is paginated, admin ones included; one row per event even with three translations. */
	@Test
	void theListIsPagedByEventsNotByTranslations() {
		for (int i = 0; i < 5; i++) {
			create("paged-" + i);
		}

		Slice<EventSummary> firstPage = this.lister.list(true, 0, 2);
		Slice<EventSummary> lastPage = this.lister.list(true, 2, 2);

		assertThat(firstPage.getContent()).hasSize(2);
		assertThat(firstPage.hasNext()).isTrue();
		assertThat(lastPage.getContent()).hasSize(1);
		assertThat(lastPage.hasNext()).isFalse();
		assertThat(firstPage.getContent().getFirst().name()).isEqualTo("Eifelsteig Etappe 1");
	}

	private static List<String> slugs(Slice<EventSummary> slice) {
		return slice.getContent().stream().map(EventSummary::slug).toList();
	}

}
