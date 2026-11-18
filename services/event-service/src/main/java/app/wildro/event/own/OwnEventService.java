package app.wildro.event.own;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.wildro.event.audit.AuditAction;
import app.wildro.event.audit.AuditRecorder;
import app.wildro.event.audit.Operator;
import app.wildro.event.domain.Event;
import app.wildro.event.domain.EventLink;
import app.wildro.event.domain.EventRepository;
import app.wildro.event.domain.EventStatus;
import app.wildro.event.domain.EventTranslation;
import app.wildro.event.domain.GeoPoints;
import app.wildro.event.domain.LinkScope;
import app.wildro.event.own.OwnEventRefusal.Code;

/**
 * Own events, SPEC.md §18.2. Every rule about who may do what lives here; the
 * rules about what an event is live in the domain and are the same ones an
 * import passes.
 *
 * Who: the owner writes, publishes and cancels; a moderator or superuser may
 * also edit and cancel, recorded in the audit log under their own id. Before
 * anything an owner does, the user service is asked whether they accepted the
 * current terms, at the moment of writing (§18.4).
 */
@Service
public class OwnEventService {

	private static final Duration DAY = Duration.ofHours(24);

	private final EventRepository events;

	private final OwnEventRepository own;

	private final WriterDirectory writers;

	private final AuditRecorder audit;

	private final OwnProperties limits;

	private final Clock clock;

	OwnEventService(EventRepository events, OwnEventRepository own, WriterDirectory writers, AuditRecorder audit,
			OwnProperties limits, Clock clock) {
		this.events = events;
		this.own = own;
		this.writers = writers;
		this.audit = audit;
		this.limits = limits;
		this.clock = clock;
	}

	@Transactional
	public OwnEventView create(Jwt token, OwnEventRequest request, boolean publish) {
		Writer writer = owner(token);
		Instant now = this.clock.instant();
		requireFuture(request, now);
		if (this.own.countUpcoming(writer.id(), now) >= this.limits.upcomingAtOnce()) {
			throw new OwnEventRefusal(Code.LIMIT_UPCOMING,
					"at most " + this.limits.upcomingAtOnce() + " upcoming events at once");
		}
		if (publish) {
			requireDailyRoom(writer.id(), now);
		}
		String slug = Slugs.forEvent(request.name(), LocalDate.ofInstant(request.startsAt(), Event.ZONE),
				this.events::existsBySlug);
		Event event = this.events.save(builder(request, writer.displayName(), writer.id())
			.slug(slug)
			.status(publish ? EventStatus.PUBLISHED : EventStatus.DRAFT)
			.publishedAt(now)
			.build());
		this.audit.record(Operator.web(writer.id()), AuditAction.EVENT_CREATED, event,
				Map.of("status", event.getStatus().name()));
		return OwnEventView.of(event);
	}

	@Transactional
	public OwnEventView update(Jwt token, Authentication authentication, String slug, OwnEventRequest request) {
		Event event = find(slug);
		Instant now = this.clock.instant();
		String organizer;
		if (event.isOwnedBy(token.getSubject())) {
			organizer = owner(token).displayName();
		}
		else if (moderates(authentication)) {
			organizer = event.getOrganizer();
		}
		else {
			throw new OwnEventRefusal(Code.NOT_YOURS, "only the owner or a moderator may edit this event");
		}
		if (event.hasPassed(now)) {
			throw new OwnEventRefusal(Code.IN_THE_PAST, "a past event is archive and is not edited");
		}
		requireFuture(request, now);
		Event candidate = builder(request, organizer, event.getOwnerUserId())
			.slug(event.getSlug())
			.status(event.getStatus() == EventStatus.PUBLISHED ? EventStatus.PUBLISHED : EventStatus.DRAFT)
			.publishedAt(event.getPublishedAt())
			.build();
		event.checkRevisableTo(candidate);
		List<String> changed = event.changedFields(candidate);
		if (!changed.isEmpty()) {
			event.revise(candidate);
			this.audit.record(Operator.web(token.getSubject()), AuditAction.EVENT_UPDATED, event,
					Map.of("changed", changed));
		}
		return OwnEventView.of(event);
	}

	@Transactional
	public OwnEventView publish(Jwt token, String slug) {
		Event event = find(slug);
		if (!event.isOwnedBy(token.getSubject())) {
			throw new OwnEventRefusal(Code.NOT_YOURS, "only the owner publishes");
		}
		Writer writer = owner(token);
		Instant now = this.clock.instant();
		if (event.getStatus() != EventStatus.DRAFT) {
			throw new OwnEventRefusal(Code.WRONG_STATE, "only a draft can be published");
		}
		if (event.hasPassed(now)) {
			throw new OwnEventRefusal(Code.IN_THE_PAST, "a past event is not published");
		}
		requireDailyRoom(writer.id(), now);
		event.renameOrganizer(writer.displayName());
		event.publish(now);
		this.audit.record(Operator.web(writer.id()), AuditAction.EVENT_PUBLISHED, event, Map.of());
		return OwnEventView.of(event);
	}

	@Transactional
	public OwnEventView cancel(Jwt token, Authentication authentication, String slug) {
		Event event = find(slug);
		if (!event.isOwnedBy(token.getSubject()) && !moderates(authentication)) {
			throw new OwnEventRefusal(Code.NOT_YOURS, "only the owner or a moderator may cancel this event");
		}
		if (event.getStatus() != EventStatus.DRAFT && event.getStatus() != EventStatus.PUBLISHED) {
			throw new OwnEventRefusal(Code.WRONG_STATE, "the event is already " + event.getStatus());
		}
		event.cancel();
		this.audit.record(Operator.web(token.getSubject()), AuditAction.EVENT_CANCELLED, event, Map.of());
		return OwnEventView.of(event);
	}

	@Transactional(readOnly = true)
	public List<OwnEventView> mine(Jwt token) {
		return this.own.findFirst100ByOwnerUserIdOrderByStartsAtDesc(token.getSubject())
			.stream()
			.map(OwnEventView::of)
			.toList();
	}

	@Transactional(readOnly = true)
	public OwnEventView one(Jwt token, Authentication authentication, String slug) {
		Event event = find(slug);
		if (!event.isOwnedBy(token.getSubject()) && !moderates(authentication)) {
			// Indistinguishable from absent: someone else's draft is not to be probed for.
			throw new OwnEventRefusal(Code.NOT_FOUND, "no such event");
		}
		return OwnEventView.of(event);
	}

	/** The writer behind the token, refused unless they may publish at all. */
	private Writer owner(Jwt token) {
		Writer writer = this.writers.whoIs(token);
		if (!writer.termsUpToDate()) {
			throw new OwnEventRefusal(Code.TERMS_REQUIRED, "accept the current terms first");
		}
		if (writer.displayName() == null || writer.displayName().isBlank()) {
			throw new OwnEventRefusal(Code.DISPLAY_NAME_REQUIRED, "an own event shows its organiser's name");
		}
		return writer;
	}

	private Event find(String slug) {
		Event event = this.events.findBySlug(slug).orElse(null);
		if (event == null || event.getOwnerUserId() == null) {
			throw new OwnEventRefusal(Code.NOT_FOUND, "no such event");
		}
		return event;
	}

	private void requireDailyRoom(String owner, Instant now) {
		if (this.own.countPublishedSince(owner, now.minus(DAY)) >= this.limits.publishedPerDay()) {
			throw new OwnEventRefusal(Code.LIMIT_PER_DAY,
					"at most " + this.limits.publishedPerDay() + " events published within 24 hours");
		}
	}

	private static void requireFuture(OwnEventRequest request, Instant now) {
		if (!request.startsAt().isAfter(now)) {
			throw new OwnEventRefusal(Code.IN_THE_PAST, "an own event starts in the future");
		}
	}

	static boolean moderates(Authentication authentication) {
		return authentication.getAuthorities()
			.stream()
			.anyMatch((a) -> a.getAuthority().equals("ROLE_moderator") || a.getAuthority().equals("ROLE_superuser"));
	}

	/**
	 * The one text, under every locale: the read path then keeps one row per event
	 * and language, and the page marks it as being in the organiser's language
	 * (SPEC.md §18.2, question 3).
	 */
	private static Event.Builder builder(OwnEventRequest request, String organizer, String ownerId) {
		EventTranslation text = new EventTranslation(request.name(), request.placeName(), request.description());
		Event.Builder builder = Event.builder()
			.ownedBy(ownerId, request.locale())
			.activityType(request.activityType())
			.difficulty(request.difficulty())
			.startsAt(request.startsAt())
			.endsAt(request.endsAt())
			.startPoint(GeoPoints.of(request.latitude(), request.longitude()))
			.organizer(organizer)
			.distanceKm(request.distanceKm())
			.elevationGainM(request.elevationGainM())
			.maxParticipants(request.maxParticipants());
		for (String locale : Event.LOCALES) {
			builder.translation(locale, text);
		}
		links(request.eventLinks()).forEach((url) -> builder.link(new EventLink(LinkScope.EVENT, url)));
		links(request.communityLinks()).forEach((url) -> builder.link(new EventLink(LinkScope.COMMUNITY, url)));
		return builder;
	}

	private static List<String> links(List<String> urls) {
		return urls == null ? List.of() : urls;
	}

}
