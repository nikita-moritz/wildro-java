package app.wildro.analytics.track;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.stereotype.Component;

import app.wildro.analytics.AnalyticsProperties;

/**
 * Cookieless visitor identity, SPEC.md §13: sha256(salt, UTC day, host, address,
 * user agent), the first 128 bits in hex.
 * <p>
 * Nothing is written to the device, so §25 TDDDG asks for no consent. Address and
 * user agent go into the hash and are dropped. The cost is deliberate: the day is
 * part of the input, so the same person is a new visitor every day and "uniques"
 * over a period is a sum of daily uniques. Freezing the salt or adding a cookie
 * would "fix" that and make the measurement need a consent banner.
 */
@Component
class VisitorHasher {

	private final Clock clock;

	private final AnalyticsProperties properties;

	VisitorHasher(Clock clock, AnalyticsProperties properties) {
		this.clock = clock;
		this.properties = properties;
	}

	Optional<String> hash(String host, String address, String userAgent) {
		if (!this.properties.hashing() || address == null || address.isBlank()) {
			return Optional.empty();
		}
		String day = LocalDate.now(this.clock).toString();
		String input = String.join("\u0000", this.properties.salt(), day, host, address,
				userAgent == null ? "" : userAgent);
		return Optional.of(HexFormat.of().formatHex(sha256(input)).substring(0, 32));
	}

	private static byte[] sha256(String input) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("every JVM ships SHA-256", ex);
		}
	}

}
