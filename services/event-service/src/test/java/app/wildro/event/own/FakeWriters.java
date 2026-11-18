package app.wildro.event.own;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.security.oauth2.jwt.Jwt;

/** The user service, in memory. Anyone not set up is a named person who accepted the terms. */
public class FakeWriters implements WriterDirectory {

	private final Map<String, Writer> writers = new ConcurrentHashMap<>();

	private final AtomicBoolean down = new AtomicBoolean();

	public void put(Writer writer) {
		this.writers.put(writer.id(), writer);
	}

	public void down(boolean down) {
		this.down.set(down);
	}

	public void reset() {
		this.writers.clear();
		this.down.set(false);
	}

	@Override
	public Writer whoIs(Jwt token) {
		if (this.down.get()) {
			throw new WriterUnavailableException("down in this test", null);
		}
		return this.writers.getOrDefault(token.getSubject(), new Writer(token.getSubject(), "Ada Lovelace", true));
	}

}
