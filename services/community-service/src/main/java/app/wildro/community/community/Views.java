package app.wildro.community.community;

import java.time.Instant;
import java.util.List;

/** What the API answers, in one place. */
public final class Views {

	private Views() {
	}

	/** A row of the public list. */
	public record Summary(String slug, String name, String area, String locale, long memberCount) {
	}

	/** The public page. Only the number of members, never their names (§18.3). */
	public record Page(long id, String slug, String name, String description, String area, String locale,
			String status, List<String> links, long memberCount, Instant createdAt, Instant closedAt) {
	}

	/** The caller's standing in one community; {@code role} null when not a member. */
	public record Standing(long id, String slug, String name, String status, Role role) {
	}

	/**
	 * A community as moderation sees it (§18.4). {@code leaders} are the owner and
	 * the admins, the only people ownership can pass to; they are filled for a
	 * superuser only, a moderator decides without names.
	 */
	public record Moderated(String slug, String name, String area, String status, String suspensionReason,
			long memberCount, Instant createdAt, List<Member> leaders) {
	}

	/** A member, as other members see them. */
	public record Member(String userId, String displayName, Role role, Instant joinedAt) {
	}

}
