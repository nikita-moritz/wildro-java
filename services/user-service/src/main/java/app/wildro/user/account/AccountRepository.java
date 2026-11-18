package app.wildro.user.account;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import app.wildro.user.identity.Identity;

/** Every statement against {@code users}. Personal data is written in exactly two places: here, and erased below. */
@Repository
public class AccountRepository {

	private final JdbcClient jdbc;

	AccountRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Optional<Account> find(String id) {
		return this.jdbc.sql("""
				SELECT id, email, display_name, status, terms_version, terms_accepted_at, erased_at
				FROM users WHERE id = :id
				""").param("id", id).query((rs, n) -> new Account(rs.getString("id"), rs.getString("email"),
				rs.getString("display_name"), rs.getString("status"), rs.getString("terms_version"),
				instant(rs.getTimestamp("terms_accepted_at")), instant(rs.getTimestamp("erased_at"))))
			.optional();
	}

	/**
	 * Creates the row from what elvix holds, or fills an erased one again for a
	 * person elvix has since restored. A row that is already filled is left
	 * alone: two first requests racing each other must not overwrite a name the
	 * person has meanwhile chosen.
	 */
	void fill(Identity identity, String displayName) {
		this.jdbc.sql("""
				INSERT INTO users (id, email, display_name)
				VALUES (:id, :email, :name)
				ON CONFLICT (id) DO UPDATE
				SET email = EXCLUDED.email, display_name = EXCLUDED.display_name,
				    erased_at = NULL, updated_at = now()
				WHERE users.erased_at IS NOT NULL
				""")
			.param("id", identity.userId())
			.param("email", identity.email())
			.param("name", displayName)
			.update();
	}

	boolean rename(String id, String displayName) {
		return this.jdbc.sql("""
				UPDATE users SET display_name = :name, updated_at = now()
				WHERE id = :id AND erased_at IS NULL
				""").param("id", id).param("name", displayName).update() == 1;
	}

	boolean acceptTerms(String id, String version, Instant at) {
		return this.jdbc.sql("""
				UPDATE users SET terms_version = :version, terms_accepted_at = :at, updated_at = now()
				WHERE id = :id AND erased_at IS NULL
				""").param("id", id).param("version", version).param("at", Timestamp.from(at)).update() == 1;
	}

	/** Only the address follows elvix after the first fill; the name is the person's own here. */
	public void updateEmail(String id, String email) {
		this.jdbc.sql("""
				UPDATE users SET email = :email, updated_at = now()
				WHERE id = :id AND erased_at IS NULL
				""").param("id", id).param("email", email).update();
	}

	/**
	 * Records a status elvix reported, unless a newer one is already recorded.
	 * A person elvix reports on before they ever opened wildro gets no row:
	 * there is nothing of theirs here to act on.
	 *
	 * @return whether the status was applied
	 */
	public boolean changeStatus(String id, String status, Instant changedAt) {
		return this.jdbc.sql("""
				UPDATE users SET status = :status, status_changed_at = :at, updated_at = now()
				WHERE id = :id AND (status_changed_at IS NULL OR status_changed_at < :at)
				""").param("id", id).param("status", status).param("at", Timestamp.from(changedAt)).update() == 1;
	}

	/** Art. 17 DSGVO: the address and the name go, the row stays for what still points at it. */
	public boolean erase(String id, Instant at) {
		return this.jdbc.sql("""
				UPDATE users SET email = NULL, display_name = NULL, terms_version = NULL,
				    terms_accepted_at = NULL, erased_at = :at, updated_at = now()
				WHERE id = :id AND erased_at IS NULL
				""").param("id", id).param("at", Timestamp.from(at)).update() == 1;
	}

	private static Instant instant(Timestamp timestamp) {
		return (timestamp != null) ? timestamp.toInstant() : null;
	}

}
