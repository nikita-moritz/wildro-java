package app.wildro.user.webhook;

import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import app.wildro.user.account.AccountRepository;
import app.wildro.user.identity.IdentityDirectory;
import tools.jackson.databind.json.JsonMapper;

/**
 * What wildro does when elvix reports a change (SPEC.md §18.1).
 *
 * <ul>
 * <li>Blocked, paused, deactivated and back again: the status is recorded. The
 * token is what actually keeps a person out, and it dies within its ten
 * minutes; published content stays, removing it is a separate moderation
 * decision.</li>
 * <li>Deleted, or left the application: the address and the name go, and a
 * {@code user.erased} entry in the outbox tells the event and community modules
 * to carry out their part.</li>
 * <li>Profile changed: the address is read again from elvix rather than taken
 * from the event, so two changes delivered out of order still end on the
 * current one.</li>
 * <li>Role changes need nothing here: roles travel in every token.</li>
 * </ul>
 */
@Component
class LifecycleHandler {

	private static final Map<String, String> STATUS_BY_TYPE = Map.of("user.banned", "BANNED", "user.unbanned",
			"ACTIVE", "user.paused", "PAUSED", "user.resumed", "ACTIVE", "user.inactivated", "INACTIVE",
			"user.reactivated", "ACTIVE", "user.restored", "ACTIVE");

	private final JdbcClient jdbc;

	private final AccountRepository accounts;

	private final IdentityDirectory directory;

	private final JsonMapper json;

	LifecycleHandler(JdbcClient jdbc, AccountRepository accounts, IdentityDirectory directory, JsonMapper json) {
		this.jdbc = jdbc;
		this.accounts = accounts;
		this.directory = directory;
		this.json = json;
	}

	private static final Set<String> OTHERS = Set.of("user.deleted", "user.left",
			"user.profile_updated", "user.identity_changed");

	/** Whether this type changes anything here; every other type is acknowledged and dropped. */
	static boolean handles(String type) {
		return STATUS_BY_TYPE.containsKey(type) || OTHERS.contains(type);
	}

	/**
	 * Applies the event once. The id is recorded in the same transaction as the
	 * change, so a delivery that fails half way is retried whole, and one that
	 * succeeded is not applied again.
	 *
	 * @return false if this event was already applied
	 */
	@Transactional
	boolean apply(WebhookEvent event) {
		int fresh = this.jdbc.sql("""
				INSERT INTO processed_webhook (event_id, type) VALUES (:id, :type)
				ON CONFLICT (event_id) DO NOTHING
				""").param("id", event.id()).param("type", event.type()).update();
		if (fresh == 0) {
			return false;
		}
		String userId = (event.data() != null) ? event.data().userId() : null;
		if (userId == null) {
			return true;
		}
		switch (event.type()) {
			case "user.deleted", "user.left" -> erase(userId, event);
			// Which fields moved is described differently across elvix versions
			// ("changed", later "categories"), and these events are rare: read
			// the address again on every one rather than parse either list.
			case "user.profile_updated", "user.identity_changed" -> this.directory.find(userId)
				.ifPresent((identity) -> this.accounts.updateEmail(userId, identity.email()));
			default -> {
				String status = STATUS_BY_TYPE.get(event.type());
				if (status != null) {
					this.accounts.changeStatus(userId, status, event.createdAt());
				}
			}
		}
		return true;
	}

	private void erase(String userId, WebhookEvent event) {
		if (!this.accounts.erase(userId, event.createdAt())) {
			// Never arrived here, or already erased: nothing of theirs to act on.
			return;
		}
		this.accounts.changeStatus(userId, "DELETED", event.createdAt());
		this.jdbc.sql("""
				INSERT INTO outbox (type, user_id, payload) VALUES ('user.erased', :user, CAST(:payload AS jsonb))
				""")
			.param("user", userId)
			.param("payload", this.json.writeValueAsString(Map.of("userId", userId, "erasedAt",
					event.createdAt().toString(), "cause", event.type())))
			.update();
	}

}
