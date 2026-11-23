package app.wildro.event.own;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import app.wildro.event.domain.ActivityType;
import app.wildro.event.domain.Difficulty;

/**
 * An own event as its author writes it: one language, the rules of SPEC.md §0
 * for everything else. The shape checks are here; the rules across fields live
 * in the domain, the same ones an import passes.
 */
public record OwnEventRequest(
		@NotNull @Pattern(regexp = "de|en|ru") String locale,
		@NotBlank @Size(max = 120) String name,
		@NotBlank @Size(max = 120) String placeName,
		@NotBlank @Size(max = 5000) String description,
		@NotNull ActivityType activityType,
		Difficulty difficulty,
		@NotNull Instant startsAt,
		Instant endsAt,
		@NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
		@NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
		BigDecimal distanceKm,
		Integer elevationGainM,
		Integer maxParticipants,
		/** What taking part costs in euros; absent means free (SPEC.md §18.2). */
		@DecimalMin("0") @DecimalMax("999999.99") BigDecimal priceAmount,
		@Size(max = 5) List<@NotBlank @Size(max = 500) String> eventLinks,
		@Size(max = 5) List<@NotBlank @Size(max = 500) String> communityLinks,
		/** Write it in this community's name (§18.3); null for a personal event. Fixed once created. */
		@Size(max = 200) String communitySlug) {
}
