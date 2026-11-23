package app.wildro.event.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * An outing someone can go on. The single entity of stage one.
 * <p>
 * It owns the table. {@code EventShort} and {@code EventPoint} are projections
 * over the same rows, not entities: three entities over one table would mean
 * three change-tracking sets and three places the schema drifts apart on the
 * first edit.
 * <p>
 * Field names used in {@link InvalidEventException} and in
 * {@link #changedFields} are the paths of the import document, so an error or a
 * diff points straight at what the author wrote.
 */
@Entity
@Table(name = "events")
public class Event {

	/**
	 * The clock the product runs on. Whether an event has passed decides what the
	 * feed shows and what the filter returns, so the answer has to be the same
	 * everywhere, which means one zone rather than the server's.
	 */
	public static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

	/** The languages every event is written in. House rule, not a per-entity choice. */
	public static final Set<String> LOCALES = Set.of("de", "en", "ru");

	/** numeric(6,2) in the schema. */
	private static final BigDecimal MAX_DISTANCE_KM = new BigDecimal("9999.99");

	/** Per scope. A page with a dozen chat links is a link farm, not an event. */
	public static final int MAX_LINKS_PER_SCOPE = 5;

	/** Tolerance for "the same point": about a tenth of a millimetre, far below any real edit. */
	private static final double POINT_TOLERANCE = 1e-9;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String slug;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EventKind kind;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EventStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "activity_type", nullable = false)
	private ActivityType activityType;

	@Enumerated(EnumType.STRING)
	private Difficulty difficulty;

	@Column(name = "starts_at", nullable = false)
	private Instant startsAt;

	@Column(name = "ends_at")
	private Instant endsAt;

	/**
	 * The moment this event stops being upcoming, as computed by
	 * {@link #computePassesAt}. Stored only so a list can filter and paginate in
	 * SQL without restating the rule there; see V3.
	 */
	@Column(name = "passes_at", nullable = false)
	private Instant passesAt;

	/**
	 * Where the outing starts. Mandatory in the schema, not only in a form check:
	 * an event without a point drops out of the filter, the search, the map and
	 * the city pages, which is everything the platform exists for.
	 */
	@Column(name = "start_point", nullable = false, columnDefinition = "geography(Point,4326)")
	private Point startPoint;

	@Column(nullable = false)
	private String organizer;

	/**
	 * Organiser, names and places in every language, folded by {@link SearchText}.
	 * Stored only so search is one indexed column instead of a join over the
	 * translations; recomputed wherever its inputs change, see V7.
	 */
	@Column(name = "search_text", nullable = false)
	private String searchText;

	@Column(name = "source_url")
	private String sourceUrl;

	@Column(name = "source_checked_at")
	private Instant sourceCheckedAt;

	@Column(name = "distance_km")
	private BigDecimal distanceKm;

	@Column(name = "elevation_gain_m")
	private Integer elevationGainM;

	/**
	 * Present for own events and future booking, never shown for an external one.
	 * A number copied out of someone's chat is not a count of places, and wiring a
	 * limit to it would have us turning people away on the strength of a year-old
	 * message.
	 */
	@Column(name = "max_participants")
	private Integer maxParticipants;

	@Column(name = "cover_image_url")
	private String coverImageUrl;

	/**
	 * The organiser's own uploaded picture of an own event, by file name; its
	 * address is in {@link #coverImageUrl}, so the read path does not tell the
	 * two kinds of cover apart. Null for an imported cover (see V14).
	 */
	@Column(name = "cover_file")
	private String coverFile;

	/** Always present with an imported cover, absent without one and with an uploaded one; see {@link CoverCredit}. */
	@jakarta.persistence.Embedded
	private CoverCredit coverCredit;

	/** The regular adult price when the source states one; see {@link Price}. */
	@jakarta.persistence.Embedded
	private Price price;

	@ElementCollection
	@CollectionTable(name = "event_translations", joinColumns = @JoinColumn(name = "event_id"))
	@MapKeyColumn(name = "locale")
	private Map<String, EventTranslation> translations = new LinkedHashMap<>();

	/**
	 * Optional links beside the source link: to the event on a network or in a
	 * messenger, and to the community behind it. One ordered list, event links
	 * first, so the table has one key and the page one order.
	 */
	@ElementCollection
	@CollectionTable(name = "event_links", joinColumns = @JoinColumn(name = "event_id"))
	@OrderColumn(name = "position")
	private List<EventLink> links = new ArrayList<>();

	/**
	 * Own events only (SPEC.md §18.2): the person who owns it, or from §18.3 the
	 * community, and the person who wrote it. elvix ids; the accounts live in the
	 * user service.
	 */
	@Column(name = "owner_user_id")
	private String ownerUserId;

	@Column(name = "owner_community_id")
	private Long ownerCommunityId;

	@Column(name = "author_user_id")
	private String authorUserId;

	/** The community's address beside its id, so a page links to it without asking (V11). */
	@Column(name = "community_slug")
	private String communitySlug;

	/** The author deleted their account; a personal event no longer shows their name. */
	@Column(name = "author_erased", nullable = false)
	private boolean authorErased;

	/** The one language an own event was written in; the text is stored under every locale. */
	@Column(name = "original_locale")
	private String originalLocale;

	/** When it first went public, for the limit on publications per day. */
	@Column(name = "published_at")
	private Instant publishedAt;

	@Column(name = "suspension_reason")
	private String suspensionReason;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	protected Event() {
		// for JPA
	}

	private Event(Builder builder) {
		this.slug = requireText(builder.slug, "slug");
		this.kind = requirePresent(builder.kind, "kind");
		this.status = requirePresent(builder.status, "status");
		this.activityType = requirePresent(builder.activityType, "activityType");
		this.difficulty = builder.difficulty;
		this.startsAt = toMicros(requirePresent(builder.startsAt, "startsAt"));
		this.endsAt = toMicros(builder.endsAt);
		this.startPoint = requirePresent(builder.startPoint, "startPoint");
		this.organizer = requireText(builder.organizer, "organizer");
		this.sourceUrl = builder.sourceUrl == null ? null : builder.sourceUrl.strip();
		this.sourceCheckedAt = toMicros(builder.sourceCheckedAt);
		this.distanceKm = normaliseDistance(builder.distanceKm);
		this.elevationGainM = builder.elevationGainM;
		this.maxParticipants = builder.maxParticipants;
		this.coverImageUrl = builder.coverImageUrl;
		this.coverFile = builder.coverFile;
		this.coverCredit = builder.coverCredit;
		// An own event is free unless its organiser names a price (SPEC.md §18.2); an
		// external one has no price when its source says nothing (V6, V15).
		this.price = builder.price == null && this.kind == EventKind.OWN ? Price.free() : builder.price;
		this.translations = new LinkedHashMap<>(builder.translations);
		this.links = orderedLinks(builder.links);
		this.ownerUserId = builder.ownerUserId;
		this.ownerCommunityId = builder.ownerCommunityId;
		this.communitySlug = builder.communitySlug;
		this.authorUserId = builder.authorUserId;
		this.originalLocale = builder.originalLocale;
		if (this.status == EventStatus.PUBLISHED) {
			this.publishedAt = toMicros(builder.publishedAt);
		}

		validate();
		this.passesAt = computePassesAt(this.startsAt, this.endsAt);
		this.searchText = SearchText.of(this.organizer, this.translations);
	}

	private void validate() {
		if (this.kind == EventKind.EXTERNAL && (this.sourceUrl == null || this.sourceUrl.isEmpty())) {
			throw new InvalidEventException("source/url", "an external event must carry a link to its source");
		}
		if (this.endsAt != null && !this.endsAt.isAfter(this.startsAt)) {
			throw new InvalidEventException("endsAt", "must be after startsAt");
		}
		if (this.elevationGainM != null && this.elevationGainM < 0) {
			throw new InvalidEventException("elevationGainM", "must not be negative");
		}
		if (this.maxParticipants != null && this.maxParticipants <= 0) {
			throw new InvalidEventException("maxParticipants", "must be positive");
		}
		if (this.coverFile == null && (this.coverImageUrl == null) != (this.coverCredit == null)) {
			throw new InvalidEventException("coverCredit",
					"a cover needs its author and licence, and a credit needs a cover");
		}
		if (this.coverFile != null
				&& (this.kind != EventKind.OWN || this.coverImageUrl == null || this.coverCredit != null)) {
			throw new InvalidEventException("cover", "an uploaded picture belongs to an own event and carries no credit");
		}
		if (this.kind == EventKind.OWN && ((this.ownerUserId == null) == (this.ownerCommunityId == null)
				|| this.authorUserId == null || this.originalLocale == null || !LOCALES.contains(this.originalLocale))) {
			throw new InvalidEventException("owner", "an own event needs an owner, an author and its language");
		}
		if (this.kind == EventKind.EXTERNAL
				&& (this.ownerUserId != null || this.ownerCommunityId != null || this.authorUserId != null)) {
			throw new InvalidEventException("owner", "an external event belongs to its source, not to a person");
		}
		if (this.status == EventStatus.SUSPENDED) {
			throw new InvalidEventException("status", "suspension is a moderation decision, not a value to write");
		}
		if (!this.translations.keySet().equals(LOCALES)) {
			throw new InvalidEventException("translations",
					"an event is written in all of " + LOCALES + ", got " + this.translations.keySet());
		}
	}

	/**
	 * Event links first, each scope in the order it was written. Also where the
	 * two rules about a list of links live: no more than a handful, and none twice.
	 */
	private static List<EventLink> orderedLinks(List<EventLink> given) {
		List<EventLink> ordered = new ArrayList<>();
		for (LinkScope scope : LinkScope.values()) {
			List<EventLink> ofScope = given.stream().filter(link -> link.getScope() == scope).toList();
			String field = "links/" + scope.documentKey();
			if (ofScope.size() > MAX_LINKS_PER_SCOPE) {
				throw new InvalidEventException(field, "at most " + MAX_LINKS_PER_SCOPE + " links");
			}
			if (new HashSet<>(ofScope).size() != ofScope.size()) {
				throw new InvalidEventException(field, "the same link appears more than once");
			}
			ordered.addAll(ofScope);
		}
		return ordered;
	}

	public List<EventLink> links(LinkScope scope) {
		return this.links.stream().filter(link -> link.getScope() == scope).toList();
	}

	/**
	 * Whether the outing is over: past its end, or past the end of the day it
	 * started on when it has no end. The single definition in the system, because
	 * the feed, the filter and the archive must agree, and two of them disagreeing
	 * is the kind of bug nobody reports and everybody sees.
	 */
	static Instant computePassesAt(Instant startsAt, Instant endsAt) {
		if (endsAt != null) {
			return endsAt;
		}
		LocalDate startDay = LocalDate.ofInstant(startsAt, ZONE);
		return startDay.plusDays(1).atStartOfDay(ZONE).toInstant();
	}

	public boolean hasPassed(Instant now) {
		return now.isAfter(this.passesAt);
	}

	/** Cancelled and draft events are off the upcoming feed; so is anything already over. */
	public boolean isUpcoming(Instant now) {
		return this.status == EventStatus.PUBLISHED && !hasPassed(now);
	}

	/**
	 * A capacity is only ever meaningful for an event we run ourselves. For an
	 * external one the number, if the source names one, belongs in the description
	 * in words.
	 */
	public Integer getVisibleCapacity() {
		return this.kind == EventKind.OWN ? this.maxParticipants : null;
	}

	public EventTranslation translation(String locale) {
		EventTranslation translation = this.translations.get(locale);
		if (translation == null) {
			throw new IllegalArgumentException("no " + locale + " translation on event " + this.slug);
		}
		return translation;
	}

	/**
	 * Refuses a revision that is really a different operation in disguise. A
	 * repeated import updates content; it never undoes a cancellation and never
	 * takes a published page back off the site, because removing a public address
	 * is its own explicit decision (SPEC.md §0.3) and the pages promised to stay
	 * for a year must not disappear because a document said "DRAFT".
	 */
	public void checkRevisableTo(Event candidate) {
		if (!this.slug.equals(candidate.slug)) {
			throw new InvalidEventException("slug", "a revision cannot change the slug");
		}
		if (this.kind != candidate.kind) {
			throw new InvalidEventException("kind", "a revision cannot change the kind of an event");
		}
		if (this.status == EventStatus.CANCELLED) {
			throw new InvalidEventException("status",
					"the event is cancelled; reinstating it is not a revision");
		}
		if (this.status == EventStatus.SUSPENDED) {
			throw new InvalidEventException("status",
					"the event was taken down by moderation; only a moderator can put it back");
		}
		if (this.status == EventStatus.PUBLISHED && candidate.status == EventStatus.DRAFT) {
			throw new InvalidEventException("status",
					"a published event cannot go back to draft; cancel or delete it explicitly");
		}
	}

	/** What a revision to {@code candidate} would change, as document paths. Changes nothing. */
	public List<String> changedFields(Event candidate) {
		List<String> changed = new ArrayList<>();
		addIfDifferent(changed, "status", this.status, candidate.status);
		addIfDifferent(changed, "activityType", this.activityType, candidate.activityType);
		addIfDifferent(changed, "difficulty", this.difficulty, candidate.difficulty);
		addIfDifferent(changed, "startsAt", this.startsAt, candidate.startsAt);
		addIfDifferent(changed, "endsAt", this.endsAt, candidate.endsAt);
		if (!this.startPoint.equalsExact(candidate.startPoint, POINT_TOLERANCE)) {
			changed.add("startPoint");
		}
		addIfDifferent(changed, "organizer", this.organizer, candidate.organizer);
		addIfDifferent(changed, "source/url", this.sourceUrl, candidate.sourceUrl);
		addIfDifferent(changed, "source/readAt", this.sourceCheckedAt, candidate.sourceCheckedAt);
		if (!sameAmount(this.distanceKm, candidate.distanceKm)) {
			changed.add("distanceKm");
		}
		addIfDifferent(changed, "elevationGainM", this.elevationGainM, candidate.elevationGainM);
		addIfDifferent(changed, "maxParticipants", this.maxParticipants, candidate.maxParticipants);
		addIfDifferent(changed, "coverImageUrl", this.coverImageUrl, candidate.coverImageUrl);
		addIfDifferent(changed, "coverFile", this.coverFile, candidate.coverFile);
		addIfDifferent(changed, "coverCredit", this.coverCredit, candidate.coverCredit);
		addIfDifferent(changed, "price", this.price, candidate.price);
		addIfDifferent(changed, "locale", this.originalLocale, candidate.originalLocale);
		for (LinkScope scope : LinkScope.values()) {
			addIfDifferent(changed, "links/" + scope.documentKey(), links(scope), candidate.links(scope));
		}
		for (String locale : LOCALES.stream().sorted().toList()) {
			addIfDifferent(changed, "translations/" + locale,
					this.translations.get(locale), candidate.translations.get(locale));
		}
		return changed;
	}

	/** Takes over the content of {@code candidate}, which has already passed every rule on its own. */
	public void revise(Event candidate) {
		checkRevisableTo(candidate);
		this.status = candidate.status;
		this.activityType = candidate.activityType;
		this.difficulty = candidate.difficulty;
		this.startsAt = candidate.startsAt;
		this.endsAt = candidate.endsAt;
		this.startPoint = candidate.startPoint;
		this.organizer = candidate.organizer;
		this.sourceUrl = candidate.sourceUrl;
		this.sourceCheckedAt = candidate.sourceCheckedAt;
		this.distanceKm = candidate.distanceKm;
		this.elevationGainM = candidate.elevationGainM;
		this.maxParticipants = candidate.maxParticipants;
		this.coverImageUrl = candidate.coverImageUrl;
		this.coverFile = candidate.coverFile;
		this.coverCredit = candidate.coverCredit;
		this.price = candidate.price;
		this.originalLocale = candidate.originalLocale;
		this.translations.clear();
		this.translations.putAll(candidate.translations);
		// Only when they differ: rewriting an identical list would still bump the row.
		if (!this.links.equals(candidate.links)) {
			this.links.clear();
			this.links.addAll(candidate.links);
		}
		this.passesAt = computePassesAt(this.startsAt, this.endsAt);
		this.searchText = SearchText.of(this.organizer, this.translations);
	}

	public void cancel() {
		this.status = EventStatus.CANCELLED;
	}

	/** A draft goes public. Publishing is one way: a published event is never a draft again. */
	public void publish(Instant at) {
		if (this.status != EventStatus.DRAFT) {
			throw new InvalidEventException("status", "only a draft can be published");
		}
		this.status = EventStatus.PUBLISHED;
		this.publishedAt = toMicros(at);
	}

	/** Moderation takes it off the site, for a stated reason (SPEC.md §18.2, DSA Art. 17). */
	public void suspend(String reason) {
		if (this.status != EventStatus.PUBLISHED) {
			throw new InvalidEventException("status", "only a published event can be taken down");
		}
		if (reason == null || reason.isBlank()) {
			throw new InvalidEventException("reason", "a removal states its reason");
		}
		this.status = EventStatus.SUSPENDED;
		this.suspensionReason = reason.strip();
	}

	/** Moderation reverses its own removal. */
	public void reinstate() {
		if (this.status != EventStatus.SUSPENDED) {
			throw new InvalidEventException("status", "only a suspended event can be put back");
		}
		this.status = EventStatus.PUBLISHED;
		this.suspensionReason = null;
	}

	/**
	 * The organiser's own picture replaces whatever cover there was.
	 * @return the file it replaces, to be deleted once this is committed, or null
	 */
	public String uploadCover(String file, String url) {
		if (this.kind != EventKind.OWN) {
			throw new InvalidEventException("cover", "only an own event takes an uploaded picture");
		}
		String previous = this.coverFile;
		this.coverFile = file;
		this.coverImageUrl = url;
		this.coverCredit = null;
		return previous;
	}

	/**
	 * The uploaded picture goes; the event shows its activity's stand-in again.
	 * @return the file that went, or null when there was none
	 */
	public String removeUploadedCover() {
		String previous = this.coverFile;
		if (previous != null) {
			this.coverFile = null;
			this.coverImageUrl = null;
		}
		return previous;
	}

	/** The author's account is gone (§18.1): their name leaves the event, the event stays. */
	public void eraseAuthor() {
		this.authorErased = true;
	}

	/** Whether this person may change it: the owner of a personal event. Moderators are decided by the caller. */
	public boolean isOwnedBy(String userId) {
		return this.kind == EventKind.OWN && userId != null && userId.equals(this.ownerUserId);
	}

	/**
	 * The owner names themselves anew on every save: a renamed person shows the
	 * new name on events they edit afterwards.
	 */
	public void renameOrganizer(String name) {
		this.organizer = requireText(name, "organizer");
		this.searchText = SearchText.of(this.organizer, this.translations);
	}

	/** Records that a human looked at the source and it still says what we say. */
	public void markSourceChecked(Instant at) {
		if (this.kind != EventKind.EXTERNAL) {
			throw new IllegalStateException("only an external event has a source to check");
		}
		this.sourceCheckedAt = toMicros(Objects.requireNonNull(at, "at is required"));
	}

	@PreUpdate
	void touch() {
		this.updatedAt = Instant.now();
	}

	private static void addIfDifferent(List<String> changed, String field, Object current, Object proposed) {
		if (!Objects.equals(current, proposed)) {
			changed.add(field);
		}
	}

	/** 18.5 and 18.50 are the same distance; BigDecimal#equals disagrees, compareTo does not. */
	private static boolean sameAmount(BigDecimal a, BigDecimal b) {
		return a == null ? b == null : b != null && a.compareTo(b) == 0;
	}

	/**
	 * The column holds microseconds. Anything finer would be dropped on the way in
	 * and then read back as a change on every repeated import.
	 */
	private static Instant toMicros(Instant instant) {
		return instant == null ? null : instant.truncatedTo(ChronoUnit.MICROS);
	}

	/** Rounded to the column's two decimals for the same reason as {@link #toMicros}. */
	private static BigDecimal normaliseDistance(BigDecimal km) {
		if (km == null) {
			return null;
		}
		BigDecimal rounded = km.setScale(2, RoundingMode.HALF_UP);
		if (rounded.signum() <= 0 || rounded.compareTo(MAX_DISTANCE_KM) > 0) {
			throw new InvalidEventException("distanceKm", "must be above 0 and at most " + MAX_DISTANCE_KM);
		}
		return rounded;
	}

	private static <T> T requirePresent(T value, String field) {
		if (value == null) {
			throw new InvalidEventException(field, "is required");
		}
		return value;
	}

	private static String requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new InvalidEventException(field, "is required and must not be blank");
		}
		return value.strip();
	}

	public Long getId() {
		return this.id;
	}

	public String getSlug() {
		return this.slug;
	}

	public EventKind getKind() {
		return this.kind;
	}

	public EventStatus getStatus() {
		return this.status;
	}

	public ActivityType getActivityType() {
		return this.activityType;
	}

	public Difficulty getDifficulty() {
		return this.difficulty;
	}

	public Instant getStartsAt() {
		return this.startsAt;
	}

	public Instant getEndsAt() {
		return this.endsAt;
	}

	public Instant getPassesAt() {
		return this.passesAt;
	}

	public Point getStartPoint() {
		return this.startPoint;
	}

	public String getOrganizer() {
		return this.organizer;
	}

	public String getSourceUrl() {
		return this.sourceUrl;
	}

	public Instant getSourceCheckedAt() {
		return this.sourceCheckedAt;
	}

	public BigDecimal getDistanceKm() {
		return this.distanceKm;
	}

	public Integer getElevationGainM() {
		return this.elevationGainM;
	}

	public String getCoverImageUrl() {
		return this.coverImageUrl;
	}

	public String getCoverFile() {
		return this.coverFile;
	}

	public CoverCredit getCoverCredit() {
		return this.coverCredit;
	}

	public Price getPrice() {
		return this.price;
	}

	public Map<String, EventTranslation> getTranslations() {
		return Map.copyOf(this.translations);
	}

	public String getOwnerUserId() {
		return this.ownerUserId;
	}

	public String getAuthorUserId() {
		return this.authorUserId;
	}

	public Long getOwnerCommunityId() {
		return this.ownerCommunityId;
	}

	public String getCommunitySlug() {
		return this.communitySlug;
	}

	public boolean isAuthorErased() {
		return this.authorErased;
	}

	/** The name to show as organiser: none for a personal event whose author deleted their account. */
	public String getShownOrganizer() {
		return this.authorErased && this.ownerUserId != null ? null : this.organizer;
	}

	public String getOriginalLocale() {
		return this.originalLocale;
	}

	public Instant getPublishedAt() {
		return this.publishedAt;
	}

	public String getSuspensionReason() {
		return this.suspensionReason;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public Instant getUpdatedAt() {
		return this.updatedAt;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private String slug;

		private EventKind kind = EventKind.EXTERNAL;

		private EventStatus status = EventStatus.DRAFT;

		private ActivityType activityType;

		private Difficulty difficulty;

		private Instant startsAt;

		private Instant endsAt;

		private Point startPoint;

		private String organizer;

		private String sourceUrl;

		private Instant sourceCheckedAt;

		private BigDecimal distanceKm;

		private Integer elevationGainM;

		private Integer maxParticipants;

		private String coverImageUrl;

		private String coverFile;

		private CoverCredit coverCredit;

		private Price price;

		private final Map<String, EventTranslation> translations = new LinkedHashMap<>();

		private final List<EventLink> links = new ArrayList<>();

		private String ownerUserId;

		private Long ownerCommunityId;

		private String communitySlug;

		private String authorUserId;

		private String originalLocale;

		private Instant publishedAt;

		public Builder slug(String slug) {
			this.slug = slug;
			return this;
		}

		public Builder kind(EventKind kind) {
			this.kind = kind;
			return this;
		}

		public Builder status(EventStatus status) {
			this.status = status;
			return this;
		}

		public Builder activityType(ActivityType activityType) {
			this.activityType = activityType;
			return this;
		}

		public Builder difficulty(Difficulty difficulty) {
			this.difficulty = difficulty;
			return this;
		}

		public Builder startsAt(Instant startsAt) {
			this.startsAt = startsAt;
			return this;
		}

		public Builder endsAt(Instant endsAt) {
			this.endsAt = endsAt;
			return this;
		}

		public Builder startPoint(Point startPoint) {
			this.startPoint = startPoint;
			return this;
		}

		public Builder organizer(String organizer) {
			this.organizer = organizer;
			return this;
		}

		public Builder source(String url, Instant checkedAt) {
			this.sourceUrl = url;
			this.sourceCheckedAt = checkedAt;
			return this;
		}

		public Builder distanceKm(BigDecimal distanceKm) {
			this.distanceKm = distanceKm;
			return this;
		}

		public Builder elevationGainM(Integer elevationGainM) {
			this.elevationGainM = elevationGainM;
			return this;
		}

		public Builder maxParticipants(Integer maxParticipants) {
			this.maxParticipants = maxParticipants;
			return this;
		}

		public Builder coverImageUrl(String coverImageUrl) {
			this.coverImageUrl = coverImageUrl;
			return this;
		}

		/** An uploaded picture carried over unchanged, as an editor's revision does. */
		public Builder uploadedCover(String file, String url) {
			this.coverFile = file;
			this.coverImageUrl = url;
			return this;
		}

		public Builder coverCredit(CoverCredit coverCredit) {
			this.coverCredit = coverCredit;
			return this;
		}

		public Builder price(Price price) {
			this.price = price;
			return this;
		}

		public Builder translation(String locale, EventTranslation translation) {
			if (!LOCALES.contains(locale)) {
				throw new InvalidEventException("translations/" + locale,
						"unknown locale, expected one of " + LOCALES);
			}
			this.translations.put(locale, translation);
			return this;
		}

		/** In the order given; the event puts event links before community links. */
		public Builder link(EventLink link) {
			this.links.add(link);
			return this;
		}

		/** An own, personal event: owned and written by this person, in this language. */
		public Builder ownedBy(String userId, String originalLocale) {
			this.kind = EventKind.OWN;
			this.ownerUserId = userId;
			this.authorUserId = userId;
			this.originalLocale = originalLocale;
			return this;
		}

		/** An own event of a community (§18.3): the community owns it, this person wrote it. */
		public Builder ownedByCommunity(long communityId, String communitySlug, String authorId, String originalLocale) {
			this.kind = EventKind.OWN;
			this.ownerUserId = null;
			this.ownerCommunityId = communityId;
			this.communitySlug = communitySlug;
			this.authorUserId = authorId;
			this.originalLocale = originalLocale;
			return this;
		}

		/** Only read when the event is built published. */
		public Builder publishedAt(Instant publishedAt) {
			this.publishedAt = publishedAt;
			return this;
		}

		public Event build() {
			return new Event(this);
		}

	}

}
