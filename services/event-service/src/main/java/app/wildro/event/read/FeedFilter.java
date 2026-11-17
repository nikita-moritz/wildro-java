package app.wildro.event.read;

import app.wildro.event.domain.ActivityType;
import app.wildro.event.domain.Difficulty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * The optional filters of the public lists and the map. Every part may be
 * absent and they combine freely, which is why they become a Specification
 * composed at runtime rather than a repository method per combination
 * (AGENTS.md, technical requirements).
 * <p>
 * Dates are calendar days in Europe/Berlin, the way a visitor picks them.
 * {@code words} is the text query, already folded by {@code SearchText.words};
 * every word must be found.
 */
public record FeedFilter(
		Set<ActivityType> types,
		Set<Difficulty> difficulties,
		LocalDate from,
		LocalDate to,
		BigDecimal minKm,
		BigDecimal maxKm,
		Near near,
		Cost cost,
		List<String> words) {

	public static final FeedFilter NONE = new FeedFilter(Set.of(), Set.of(), null, null, null, null, null, null, List.of());

	/** Without a text query: what every caller wrote before search existed. */
	public FeedFilter(Set<ActivityType> types, Set<Difficulty> difficulties, LocalDate from, LocalDate to,
			BigDecimal minKm, BigDecimal maxKm, Near near, Cost cost) {
		this(types, difficulties, from, to, minKm, maxKm, near, cost, List.of());
	}

	public FeedFilter {
		words = words == null ? List.of() : List.copyOf(words);
		types = types == null ? Set.of() : Set.copyOf(types);
		difficulties = difficulties == null ? Set.of() : Set.copyOf(difficulties);
		if (from != null && to != null && to.isBefore(from)) {
			throw new IllegalArgumentException("to must not be before from");
		}
		if (minKm != null && maxKm != null && maxKm.compareTo(minKm) < 0) {
			throw new IllegalArgumentException("maxKm must not be below minKm");
		}
	}

	/**
	 * Free or paid, as the stated price says. An event whose source names no price
	 * matches neither: it is not known to be either.
	 */
	public enum Cost {
		FREE, PAID
	}

	/**
	 * "Near me": a point the visitor's browser supplied after they asked, and a
	 * radius. Used for this one query and never stored (SPEC.md §0.4).
	 */
	public record Near(double latitude, double longitude, int radiusKm) {

		public static final int MAX_RADIUS_KM = 300;

		public Near {
			if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
				throw new IllegalArgumentException("coordinates out of range");
			}
			if (radiusKm < 1 || radiusKm > MAX_RADIUS_KM) {
				throw new IllegalArgumentException("radiusKm must be between 1 and " + MAX_RADIUS_KM);
			}
		}

	}

}
