package app.wildro.user.webhook;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * elvix's delivery signature: {@code Elvix-Signature: t=<unix seconds>,v1=<hex>},
 * where the hex is HMAC-SHA256 over {@code "<t>.<raw body>"} with the shared
 * secret. The time is inside the signed string, so it cannot be moved without
 * breaking the signature; checking it against our clock is what turns a
 * captured delivery into a useless one after the tolerance has passed.
 */
final class WebhookSignature {

	private WebhookSignature() {
	}

	static boolean valid(String header, String rawBody, String secret, Clock clock, Duration tolerance) {
		if (header == null || secret == null || secret.isBlank()) {
			return false;
		}
		Long timestamp = null;
		List<String> signatures = new ArrayList<>();
		for (String part : header.split(",")) {
			String[] pair = part.strip().split("=", 2);
			if (pair.length == 2 && pair[0].equals("t")) {
				timestamp = parseLong(pair[1]);
			}
			else if (pair.length == 2 && pair[0].equals("v1")) {
				signatures.add(pair[1]);
			}
		}
		if (timestamp == null || signatures.isEmpty()) {
			return false;
		}
		byte[] expected = hmac(secret, timestamp + "." + rawBody).getBytes(StandardCharsets.US_ASCII);
		// Several v1 entries are allowed, which is how a secret rotation is signed.
		boolean signed = signatures.stream()
			.anyMatch((candidate) -> MessageDigest.isEqual(expected, candidate.getBytes(StandardCharsets.US_ASCII)));
		return signed && Duration.between(Instant.ofEpochSecond(timestamp), clock.instant())
			.abs()
			.compareTo(tolerance) <= 0;
	}

	static String hmac(String secret, String payload) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException | InvalidKeyException ex) {
			throw new IllegalStateException("HmacSHA256 is part of every JDK", ex);
		}
	}

	private static Long parseLong(String value) {
		try {
			return Long.parseLong(value);
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

}
