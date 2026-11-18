package app.wildro.user;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import app.wildro.user.identity.Identity;
import app.wildro.user.identity.IdentityDirectory;
import app.wildro.user.identity.IdentityUnavailableException;

/** elvix's member lookup, held in memory. Tests add people, take elvix down and count the calls. */
public class FakeDirectory implements IdentityDirectory {

	private final Map<String, Identity> people = new ConcurrentHashMap<>();

	private final AtomicBoolean down = new AtomicBoolean();

	private final AtomicInteger calls = new AtomicInteger();

	public void put(Identity identity) {
		this.people.put(identity.userId(), identity);
	}

	public void down(boolean down) {
		this.down.set(down);
	}

	public int calls() {
		return this.calls.get();
	}

	public void reset() {
		this.people.clear();
		this.down.set(false);
		this.calls.set(0);
	}

	@Override
	public Optional<Identity> find(String userId) {
		this.calls.incrementAndGet();
		if (this.down.get()) {
			throw new IdentityUnavailableException("elvix is down in this test", null);
		}
		return Optional.ofNullable(this.people.get(userId));
	}

}
