package app.wildro.user.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The shared secret the services present to each other on {@code /internal/**}
 * (header {@code X-Internal-Token}). Everything listens on loopback already;
 * this is the second lock, so a stray local process cannot read the outbox.
 * Blank refuses every call.
 */
@Component
public class InternalToken {

	private final byte[] expected;

	InternalToken(@Value("${wildro.internal-token:}") String token) {
		this.expected = token.getBytes(StandardCharsets.UTF_8);
	}

	public boolean matches(String presented) {
		return this.expected.length >= 32 && presented != null
				&& MessageDigest.isEqual(this.expected, presented.getBytes(StandardCharsets.UTF_8));
	}

}
