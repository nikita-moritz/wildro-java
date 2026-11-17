package app.wildro.event.admin;

import app.wildro.event.admin.ImportDocument.EventDocument;
import app.wildro.event.admin.ImportDocument.Translation;
import app.wildro.event.admin.ImportOutcome.Action;
import app.wildro.event.admin.ImportOutcome.Item;
import app.wildro.event.admin.ImportOutcome.Mode;
import app.wildro.event.audit.AuditAction;
import app.wildro.event.audit.AuditRecorder;
import app.wildro.event.audit.Operator;
import app.wildro.event.domain.ActivityType;
import app.wildro.event.domain.CoverCredit;
import app.wildro.event.domain.Difficulty;
import app.wildro.event.domain.Event;
import app.wildro.event.domain.EventKind;
import app.wildro.event.domain.EventLink;
import app.wildro.event.domain.EventRepository;
import app.wildro.event.domain.EventStatus;
import app.wildro.event.domain.EventTranslation;
import app.wildro.event.domain.GeoPoints;
import app.wildro.event.domain.InvalidEventException;
import app.wildro.event.domain.LinkScope;
import app.wildro.event.domain.Price;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Creates and revises events from an import document (SPEC.md §0.1).
 * <p>
 * The command line is a thin shell over this class and the panel will be one
 * too: every rule lives here or in the domain, none in a channel, so the two
 * cannot grow rules of their own and drift apart.
 * <p>
 * Checking happens in three layers, each catching what the one before cannot:
 * the published schema (shape, types, enumerations, unknown keys), the domain
 * (rules across fields, such as an end before a start), and the current state
 * (a cancelled event cannot be revived by importing it again).
 */
@Service
public class EventImporter {

	private final ImportSchema schema;

	private final JsonMapper json;

	private final EventRepository events;

	private final AuditRecorder audit;

	EventImporter(ImportSchema schema, JsonMapper json, EventRepository events, AuditRecorder audit) {
		this.schema = schema;
		this.json = json;
		this.events = events;
		this.audit = audit;
	}

	/**
	 * What {@link #apply} would do, without doing it. Read-only, so Hibernate does
	 * not even flush: a dry run cannot write by accident.
	 */
	@Transactional(readOnly = true)
	public ImportOutcome plan(JsonNode document) {
		return evaluate(document, Mode.DRY_RUN).outcome();
	}

	/**
	 * Writes the whole document or nothing. One transaction: any violation found
	 * up front writes nothing, and any failure halfway rolls back what came
	 * before it, audit entries included.
	 */
	@Transactional
	public ImportOutcome apply(JsonNode document, Operator operator) {
		Evaluation evaluation = evaluate(document, Mode.WRITE);
		if (!evaluation.outcome().ok()) {
			return evaluation.outcome();
		}
		for (Step step : evaluation.steps()) {
			switch (step.action()) {
				case CREATE -> {
					Event saved = this.events.save(step.candidate());
					this.audit.record(operator, AuditAction.EVENT_CREATED, saved, Map.of());
				}
				case UPDATE -> {
					step.existing().revise(step.candidate());
					this.audit.record(operator, AuditAction.EVENT_UPDATED, step.existing(),
							Map.of("changed", step.changedFields()));
				}
				case UNCHANGED -> {
					// nothing to write, and nothing happened worth an audit line
				}
			}
		}
		// Surfaces a constraint the domain did not know about here, inside the
		// transaction and this method, rather than at commit in someone else's frame.
		this.events.flush();
		return evaluation.outcome();
	}

	private Evaluation evaluate(JsonNode document, Mode mode) {
		List<Violation> schemaViolations = this.schema.validate(document);
		if (!schemaViolations.isEmpty()) {
			return new Evaluation(ImportOutcome.refused(mode, schemaViolations), List.of());
		}

		List<EventDocument> documents = this.json.treeToValue(document, ImportDocument.class).events();
		Map<String, Event> existing = this.events
				.findBySlugIn(documents.stream().map(EventDocument::slug).toList())
				.stream()
				.collect(Collectors.toMap(Event::getSlug, Function.identity()));

		List<Violation> violations = new ArrayList<>();
		List<Step> steps = new ArrayList<>();
		Set<String> seen = new HashSet<>();

		for (int index = 0; index < documents.size(); index++) {
			EventDocument doc = documents.get(index);
			String base = "/events/" + index;

			if (!seen.add(doc.slug())) {
				violations.add(new Violation(base + "/slug", "appears more than once in this document"));
				continue;
			}

			Event candidate;
			try {
				candidate = toEvent(doc);
			}
			catch (InvalidEventException ex) {
				violations.add(new Violation(base + "/" + ex.getField(), ex.getReason()));
				continue;
			}

			Event current = existing.get(doc.slug());
			if (current == null) {
				steps.add(new Step(index, Action.CREATE, null, candidate, List.of()));
				continue;
			}
			try {
				current.checkRevisableTo(candidate);
			}
			catch (InvalidEventException ex) {
				violations.add(new Violation(base + "/" + ex.getField(), ex.getReason()));
				continue;
			}
			List<String> changed = current.changedFields(candidate);
			steps.add(new Step(index, changed.isEmpty() ? Action.UNCHANGED : Action.UPDATE, current, candidate, changed));
		}

		if (!violations.isEmpty()) {
			return new Evaluation(ImportOutcome.refused(mode, violations), List.of());
		}
		List<Item> items = steps.stream()
				.map(step -> new Item(step.index(), step.candidate().getSlug(), step.action(), step.changedFields()))
				.toList();
		return new Evaluation(new ImportOutcome(mode, List.of(), items), steps);
	}

	/** Stage one knows only external events, so the kind is not the document's to choose. */
	private static Event toEvent(EventDocument doc) {
		Event.Builder builder = Event.builder()
				.slug(doc.slug())
				.kind(EventKind.EXTERNAL)
				.status(EventStatus.valueOf(doc.status()))
				.activityType(ActivityType.valueOf(doc.activityType()))
				.difficulty(doc.difficulty() == null ? null : Difficulty.valueOf(doc.difficulty()))
				.startsAt(instant(doc.startsAt(), "startsAt"))
				.endsAt(doc.endsAt() == null ? null : instant(doc.endsAt(), "endsAt"))
				.startPoint(GeoPoints.of(doc.startPoint().lat(), doc.startPoint().lon()))
				.organizer(doc.organizer())
				.source(doc.source().url(), instant(doc.source().readAt(), "source/readAt"))
				.distanceKm(doc.distanceKm())
				.elevationGainM(doc.elevationGainM())
				.coverImageUrl(doc.coverImageUrl())
				.coverCredit(credit(doc.coverCredit()))
				.price(price(doc.price()));
		if (doc.links() != null) {
			addLinks(builder, LinkScope.EVENT, doc.links().event());
			addLinks(builder, LinkScope.COMMUNITY, doc.links().community());
		}
		builder.translation("de", translation("de", doc.translations().de()));
		builder.translation("en", translation("en", doc.translations().en()));
		builder.translation("ru", translation("ru", doc.translations().ru()));
		return builder.build();
	}

	/** The credit does not know where in the document it stood, so its errors are placed here. */
	private static CoverCredit credit(ImportDocument.Credit doc) {
		if (doc == null) {
			return null;
		}
		try {
			return new CoverCredit(doc.author(), doc.license(), doc.licenseUrl(), doc.sourceUrl());
		}
		catch (InvalidEventException ex) {
			throw new InvalidEventException("coverCredit/" + ex.getField(), ex.getReason());
		}
	}

	/** The price does not know where in the document it stood, so its errors are placed here. */
	private static Price price(ImportDocument.Price doc) {
		if (doc == null) {
			return null;
		}
		try {
			return new Price(doc.amount(), doc.currency());
		}
		catch (InvalidEventException ex) {
			throw new InvalidEventException("price/" + ex.getField(), ex.getReason());
		}
	}

	/** A link does not know where in the document it stood, so its errors are placed here. */
	private static void addLinks(Event.Builder builder, LinkScope scope, List<String> urls) {
		if (urls == null) {
			return;
		}
		for (int index = 0; index < urls.size(); index++) {
			try {
				builder.link(new EventLink(scope, urls.get(index)));
			}
			catch (InvalidEventException ex) {
				throw new InvalidEventException("links/" + scope.documentKey() + "/" + index, ex.getReason());
			}
		}
	}

	/** The translation does not know its own language, so its errors are placed here. */
	private static EventTranslation translation(String locale, Translation doc) {
		try {
			return new EventTranslation(doc.name(), doc.placeName(), doc.description());
		}
		catch (InvalidEventException ex) {
			throw new InvalidEventException("translations/" + locale + "/" + ex.getField(), ex.getReason());
		}
	}

	private static Instant instant(String value, String field) {
		try {
			return OffsetDateTime.parse(value).toInstant();
		}
		catch (DateTimeParseException ex) {
			throw new InvalidEventException(field, "is not a date-time with an offset: " + value);
		}
	}

	/** One event's planned fate, carrying what {@link #apply} needs to carry it out. */
	private record Step(int index, Action action, Event existing, Event candidate, List<String> changedFields) {
	}

	private record Evaluation(ImportOutcome outcome, List<Step> steps) {
	}

}
