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
import app.wildro.event.domain.EventKind;
import app.wildro.event.domain.EventLink;
import app.wildro.event.domain.EventRepository;
import app.wildro.event.domain.EventStatus;
import app.wildro.event.domain.EventTranslation;
import app.wildro.event.domain.GeoPoints;
import app.wildro.event.domain.LinkScope;
import app.wildro.event.domain.Price;
import app.wildro.event.media.CoverFiles;
import app.wildro.event.own.OwnEventRefusal.Code;

/**
 * Own events, SPEC.md §18.2, and events of communities, §18.3. Every rule about
 * who may do what lives here; what an event is lives in the domain, the same
 * rules an import passes.
 *
 * Who: a personal event is its owner's; a community's event belongs to whoever
 * is OWNER or ADMIN of that community at the moment of writing, asked from the
 * community service every time (§18.4). A moderator or superuser may also edit
 * and cancel, audited under their own id. Whoever publishes must have accepted
 * the terms in force, asked from the user service at the moment of writing.
 */
@Service
public class OwnEventService {

	private static final Duration DAY = Duration.ofHours(24);

	private final EventRepository events;

	private final OwnEventRepository own;

	private final WriterDirectory writers;

	private final CommunityGateway communities;

	private final AuditRecorder audit;

	private final OwnProperties limits;

	private final Clock clock;

	private final CoverFiles covers;

	OwnEventService(EventRepository events, OwnEventRepository own, WriterDirectory writers,
			CommunityGateway communities, AuditRecorder audit, OwnProperties limits, Clock clock, CoverFiles covers) {
		this.events = events;
		this.own = own;
		this.writers = writers;
		this.communities = communities;
		this.audit = audit;
		this.limits = limits;
		this.clock = clock;
		this.covers = covers;
	}

	@Transactional
	public OwnEventView create(Jwt token, OwnEventRequest request, boolean publish) {
		Writer writer = publisher(token);
		Instant now = this.clock.instant();
		requireFuture(request, now);
		if (this.own.countUpcoming(writer.id(), now) >= this.limits.upcomingAtOnce()) {
			throw new OwnEventRefusal(Code.LIMIT_UPCOMING,
					"at most " + this.limits.upcomingAtOnce() + " upcoming events at once");
		}
		if (publish) {
			requireDailyRoom(writer.id(), now);
		}
		Event.Builder builder;
		if (request.communitySlug() != null && !request.communitySlug().isBlank()) {
			CommunityGateway.Standing community = managedCommunity(token, request.communitySlug());
			builder = builder(request, community.name())
				.ownedByCommunity(community.id(), community.slug(), writer.id(), request.locale());
		}
		else {
			builder = builder(request, writer.displayName()).ownedBy(writer.id(), request.locale());
		}
		String slug = Slugs.forEvent(request.name(), LocalDate.ofInstant(request.startsAt(), Event.ZONE),
				this.events::existsBySlug);
		Event event = this.events.save(builder.slug(slug)
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
		String organizer = organizerFor(event, token, authentication);
		if (event.hasPassed(now)) {
			throw new OwnEventRefusal(Code.IN_THE_PAST, "a past event is archive and is not edited");
		}
		requireFuture(request, now);
		Event.Builder builder = builder(request, organizer);
		if (event.getOwnerCommunityId() != null) {
			builder.ownedByCommunity(event.getOwnerCommunityId(), event.getCommunitySlug(), event.getAuthorUserId(),
					request.locale());
		}
		else {
			builder.ownedBy(event.getOwnerUserId(), request.locale());
		}
		// The editor does not carry the picture: it stays as it is.
		if (event.getCoverFile() != null) {
			builder.uploadedCover(event.getCoverFile(), event.getCoverImageUrl());
		}
		Event candidate = builder.slug(event.getSlug())
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
		String organizer = organizerFor(event, token, null);
		Writer writer = publisher(token);
		Instant now = this.clock.instant();
		if (event.getStatus() != EventStatus.DRAFT) {
			throw new OwnEventRefusal(Code.WRONG_STATE, "only a draft can be published");
		}
		if (event.hasPassed(now)) {
			throw new OwnEventRefusal(Code.IN_THE_PAST, "a past event is not published");
		}
		requireDailyRoom(writer.id(), now);
		event.renameOrganizer(organizer);
		event.publish(now);
		this.audit.record(Operator.web(writer.id()), AuditAction.EVENT_PUBLISHED, event, Map.of());
		return OwnEventView.of(event);
	}

	@Transactional
	public OwnEventView cancel(Jwt token, Authentication authentication, String slug) {
		Event event = find(slug);
		organizerFor(event, token, authentication);
		if (event.getStatus() != EventStatus.DRAFT && event.getStatus() != EventStatus.PUBLISHED) {
			throw new OwnEventRefusal(Code.WRONG_STATE, "the event is already " + event.getStatus());
		}
		event.cancel();
		this.audit.record(Operator.web(token.getSubject()), AuditAction.EVENT_CANCELLED, event, Map.of());
		return OwnEventView.of(event);
	}

	/**
	 * The organiser's own picture for an event still ahead, draft or published.
	 * Whoever may edit the event may set it; the old file goes after the commit.
	 */
	@Transactional
	public OwnEventView uploadCover(Jwt token, Authentication authentication, String slug, byte[] webp) {
		Event event = changeableCover(token, authentication, slug);
		if (!this.covers.enabled()) {
			throw new OwnEventRefusal(Code.UNAVAILABLE, "pictures are not configured");
		}
		if (!CoverFiles.acceptable(webp)) {
			throw new OwnEventRefusal(Code.INVALID, "a WebP picture of at most " + CoverFiles.MAX_BYTES + " bytes");
		}
		String file = this.covers.store(event.getId(), webp);
		if (!file.equals(event.getCoverFile())) {
			this.covers.deleteAfterCommit(event.uploadCover(file, this.covers.urlOf(file)));
			this.audit.record(Operator.web(token.getSubject()), AuditAction.COVER_CHANGED, event, Map.of("file", file));
		}
		return OwnEventView.of(event);
	}

	/** Takes the uploaded picture away again; the activity's stand-in shows instead. */
	@Transactional
	public OwnEventView removeCover(Jwt token, Authentication authentication, String slug) {
		Event event = changeableCover(token, authentication, slug);
		String previous = event.removeUploadedCover();
		if (previous != null) {
			this.covers.deleteAfterCommit(previous);
			this.audit.record(Operator.web(token.getSubject()), AuditAction.COVER_CHANGED, event,
					Map.of("removed", previous));
		}
		return OwnEventView.of(event);
	}

	private Event changeableCover(Jwt token, Authentication authentication, String slug) {
		Event event = find(slug);
		organizerFor(event, token, authentication);
		if (event.getStatus() != EventStatus.DRAFT && event.getStatus() != EventStatus.PUBLISHED) {
			throw new OwnEventRefusal(Code.WRONG_STATE, "the event is " + event.getStatus());
		}
		if (event.hasPassed(this.clock.instant())) {
			throw new OwnEventRefusal(Code.IN_THE_PAST, "a past event is archive and is not edited");
		}
		return event;
	}

	@Transactional(readOnly = true)
	public List<OwnEventView> mine(Jwt token) {
		return this.own.mine(token.getSubject()).stream().map(OwnEventView::of).toList();
	}

	/**
	 * All events of a community, for its owner and admins only: what each of them
	 * wrote, drafts included, since any of them may edit any (§18.4).
	 */
	@Transactional(readOnly = true)
	public List<OwnEventView> ofCommunity(Jwt token, String communitySlug) {
		CommunityGateway.Standing community = managedCommunity(token, communitySlug);
		return this.own.ofCommunity(community.id()).stream().map(OwnEventView::of).toList();
	}

	@Transactional(readOnly = true)
	public OwnEventView one(Jwt token, Authentication authentication, String slug) {
		Event event = find(slug);
		try {
			organizerFor(event, token, authentication);
		}
		catch (OwnEventRefusal refusal) {
			// Indistinguishable from absent: someone else's draft is not to be probed for.
			throw new OwnEventRefusal(Code.NOT_FOUND, "no such event");
		}
		return OwnEventView.of(event);
	}

	/**
	 * Decides whether the caller may change this event, and under which organiser
	 * name it then appears: the owner's current name for a personal event, the
	 * community's current name for a community's, unchanged when a moderator
	 * edits. Refuses everyone else.
	 */
	private String organizerFor(Event event, Jwt token, Authentication authentication) {
		if (event.getOwnerCommunityId() != null) {
			CommunityGateway.Standing community = this.communities.standing(token, event.getCommunitySlug());
			if (community != null && community.manages()) {
				return community.name();
			}
		}
		else if (event.isOwnedBy(token.getSubject())) {
			return publisher(token).displayName();
		}
		if (authentication != null && moderates(authentication)) {
			return event.getOrganizer();
		}
		throw new OwnEventRefusal(Code.NOT_YOURS, "only the owner, the community's admins or a moderator may do this");
	}

	private CommunityGateway.Standing managedCommunity(Jwt token, String slug) {
		CommunityGateway.Standing community = this.communities.standing(token, slug);
		if (community == null) {
			throw new OwnEventRefusal(Code.NOT_FOUND, "no such community");
		}
		if (!community.manages()) {
			throw new OwnEventRefusal(Code.NOT_YOURS, "only an owner or admin writes events for a community");
		}
		return community;
	}

	/** The person behind the token, refused unless they may publish at all. */
	private Writer publisher(Jwt token) {
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
		if (event == null || event.getKind() != EventKind.OWN) {
			throw new OwnEventRefusal(Code.NOT_FOUND, "no such event");
		}
		return event;
	}

	private void requireDailyRoom(String author, Instant now) {
		if (this.own.countPublishedSince(author, now.minus(DAY)) >= this.limits.publishedPerDay()) {
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
	 * (SPEC.md §18.2, question 3). Ownership is set by the caller.
	 */
	private static Event.Builder builder(OwnEventRequest request, String organizer) {
		EventTranslation text = new EventTranslation(request.name(), request.placeName(), request.description());
		Event.Builder builder = Event.builder()
			.activityType(request.activityType())
			.difficulty(request.difficulty())
			.startsAt(request.startsAt())
			.endsAt(request.endsAt())
			.startPoint(GeoPoints.of(request.latitude(), request.longitude()))
			.organizer(organizer)
			.distanceKm(request.distanceKm())
			.elevationGainM(request.elevationGainM())
			.maxParticipants(request.maxParticipants())
			// No price named: the domain makes an own event free (SPEC.md §18.2).
			.price(request.priceAmount() == null ? null : new Price(request.priceAmount(), "EUR"));
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
