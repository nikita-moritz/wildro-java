package app.wildro.community.community;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.wildro.community.community.CommunityRefusal.Code;
import app.wildro.community.people.PeopleDirectory;
import app.wildro.community.people.Person;

/**
 * The rules of SPEC.md §18.3, checked on the server for every change (§18.4).
 *
 * <ul>
 * <li>The creator owns it; one owner, always. Ownership moves only to an ADMIN,
 * as its own action, audited. The owner cannot be demoted, removed or leave
 * before handing over.</li>
 * <li>Anyone signed in joins with one press; an ADMIN removes members, the
 * OWNER also admins. Only the OWNER changes roles.</li>
 * <li>Closing cancels the community's future events (through the outbox), and
 * the community stays visible as closed so links keep working.</li>
 * <li>One person owns at most three communities that are not closed.</li>
 * </ul>
 */
@Service
public class CommunityService {

	private final CommunityStore store;

	private final PeopleDirectory people;

	private final Clock clock;

	private final int ownedAtMost;

	CommunityService(CommunityStore store, PeopleDirectory people, Clock clock,
			@Value("${wildro.communities.owned-at-most}") int ownedAtMost) {
		this.store = store;
		this.people = people;
		this.clock = clock;
		this.ownedAtMost = ownedAtMost;
	}

	@Transactional(readOnly = true)
	public List<Views.Summary> list(int page, int size) {
		return this.store.listActive(page, size);
	}

	/** The public page; a suspended community is off the site. */
	@Transactional(readOnly = true)
	public Views.Page page(String slug) {
		CommunityStore.Row row = visible(slug);
		return new Views.Page(row.id(), row.slug(), row.name(), row.description(), row.area(), row.locale(),
				row.status(), this.store.links(row.id()), this.store.memberCount(row.id()), row.createdAt(),
				row.closedAt());
	}

	/** The caller's role here, also what the event service asks before an event is written in this community's name. */
	@Transactional(readOnly = true)
	public Views.Standing standing(Jwt token, String slug) {
		CommunityStore.Row row = visible(slug);
		return new Views.Standing(row.id(), row.slug(), row.name(), row.status(),
				this.store.roleOf(row.id(), token.getSubject()).orElse(null));
	}

	@Transactional(readOnly = true)
	public List<Views.Standing> mine(Jwt token) {
		return this.store.standingsOf(token.getSubject());
	}

	/** Names are for members only (§18.3); outside the community a person's name is never shown. */
	@Transactional(readOnly = true)
	public List<Views.Member> members(Jwt token, String slug) {
		CommunityStore.Row row = visible(slug);
		if (this.store.roleOf(row.id(), token.getSubject()).isEmpty()) {
			throw new CommunityRefusal(Code.FORBIDDEN, "only members see who is a member");
		}
		return this.store.members(row.id());
	}

	@Transactional
	public Views.Page create(Jwt token, CommunityRequest request) {
		Person person = publisher(token);
		if (this.store.ownedActive(person.id()) >= this.ownedAtMost) {
			throw new CommunityRefusal(Code.LIMIT_OWNED, "one person owns at most " + this.ownedAtMost + " communities");
		}
		String slug = Slugs.forCommunity(request.name(), this.store::slugTaken);
		long id = this.store.create(slug, request);
		this.store.join(id, person.id(), person.displayName(), Role.OWNER, this.clock.instant());
		this.store.audit(person.id(), "COMMUNITY_CREATED", id, Map.of("slug", slug));
		return page(slug);
	}

	@Transactional
	public Views.Page update(Jwt token, String slug, CommunityRequest request) {
		CommunityStore.Row row = active(slug);
		Person person = publisher(token);
		requireRole(row, person.id(), true);
		this.store.update(row.id(), request);
		this.store.refreshName(row.id(), person.id(), person.displayName());
		this.store.audit(person.id(), "COMMUNITY_UPDATED", row.id(), Map.of());
		return page(slug);
	}

	/** Joining needs no terms: only publishing does (§18.1). It does need a name members can see. */
	@Transactional
	public Views.Standing join(Jwt token, String slug) {
		CommunityStore.Row row = active(slug);
		if (this.store.roleOf(row.id(), token.getSubject()).isEmpty()) {
			Person person = this.people.whoIs(token);
			if (blank(person.displayName())) {
				throw new CommunityRefusal(Code.DISPLAY_NAME_REQUIRED, "members see each other's names");
			}
			this.store.join(row.id(), person.id(), person.displayName(), Role.MEMBER, this.clock.instant());
		}
		return standing(token, slug);
	}

	@Transactional
	public Views.Standing leave(Jwt token, String slug) {
		CommunityStore.Row row = visible(slug);
		Role role = this.store.roleOf(row.id(), token.getSubject()).orElse(null);
		if (role == Role.OWNER) {
			throw new CommunityRefusal(Code.OWNER_CANNOT_LEAVE, "hand over ownership to an admin first");
		}
		this.store.leave(row.id(), token.getSubject());
		return standing(token, slug);
	}

	/** OWNER only: makes someone ADMIN or MEMBER. The owner's own role is not changed this way. */
	@Transactional
	public void setRole(Jwt token, String slug, String userId, Role role) {
		CommunityStore.Row row = active(slug);
		if (this.store.roleOf(row.id(), token.getSubject()).orElse(null) != Role.OWNER) {
			throw new CommunityRefusal(Code.FORBIDDEN, "only the owner changes roles");
		}
		Role current = member(row, userId);
		if (role == Role.OWNER || current == Role.OWNER) {
			throw new CommunityRefusal(Code.WRONG_STATE, "ownership moves only by handing it over");
		}
		if (current != role) {
			this.store.setRole(row.id(), userId, role, this.clock.instant());
			this.store.audit(token.getSubject(), "ROLE_CHANGED", row.id(), Map.of("user", userId, "role", role.name()));
		}
	}

	/** An ADMIN removes a MEMBER; the OWNER removes anyone but themselves. */
	@Transactional
	public void remove(Jwt token, String slug, String userId) {
		CommunityStore.Row row = visible(slug);
		Role actor = this.store.roleOf(row.id(), token.getSubject()).orElse(null);
		Role target = member(row, userId);
		boolean allowed = target != Role.OWNER
				&& (actor == Role.OWNER || (actor == Role.ADMIN && target == Role.MEMBER));
		if (!allowed) {
			throw new CommunityRefusal(Code.FORBIDDEN, "not allowed to remove this member");
		}
		this.store.leave(row.id(), userId);
		this.store.audit(token.getSubject(), "MEMBER_REMOVED", row.id(), Map.of("user", userId));
	}

	@Transactional
	public void transfer(Jwt token, String slug, String toUserId) {
		CommunityStore.Row row = active(slug);
		if (this.store.roleOf(row.id(), token.getSubject()).orElse(null) != Role.OWNER) {
			throw new CommunityRefusal(Code.FORBIDDEN, "only the owner hands over ownership");
		}
		handOver(row.id(), token.getSubject(), toUserId, token.getSubject());
	}

	/**
	 * Moves ownership to an ADMIN, in one transaction: the old owner is demoted
	 * first, so the one-owner index holds at every statement.
	 */
	void handOver(long community, String fromUserId, String toUserId, String actor) {
		if (this.store.roleOf(community, toUserId).orElse(null) != Role.ADMIN) {
			throw new CommunityRefusal(Code.TARGET_NOT_ADMIN, "ownership goes only to an admin");
		}
		Instant now = this.clock.instant();
		if (fromUserId != null) {
			this.store.setRole(community, fromUserId, Role.ADMIN, now);
		}
		this.store.setRole(community, toUserId, Role.OWNER, now);
		this.store.audit(actor, "OWNERSHIP_TRANSFERRED", community,
				fromUserId == null ? Map.of("to", toUserId) : Map.of("from", fromUserId, "to", toUserId));
	}

	@Transactional
	public void close(Jwt token, String slug) {
		CommunityStore.Row row = active(slug);
		if (this.store.roleOf(row.id(), token.getSubject()).orElse(null) != Role.OWNER) {
			throw new CommunityRefusal(Code.FORBIDDEN, "only the owner closes the community");
		}
		closeCommunity(row.id(), token.getSubject(), "owner");
	}

	/** Status, audit and the event service's instruction, in one transaction. */
	void closeCommunity(long community, String actor, String cause) {
		Instant now = this.clock.instant();
		this.store.setStatus(community, "CLOSED", null, now);
		this.store.audit(actor, "COMMUNITY_CLOSED", community, Map.of("cause", cause));
		this.store.outbox("community.closed", community, Map.of("communityId", community, "closedAt", now.toString()));
	}

	/**
	 * The person's account was deleted (user.erased from the user service, §18.1):
	 * memberships go; an owned community passes to the longest-serving ADMIN, or
	 * closes when there is none. wildro cannot refuse a deletion.
	 */
	@Transactional
	public void erase(String userId) {
		for (Views.Standing standing : this.store.standingsOf(userId)) {
			if (standing.role() == Role.OWNER && !"CLOSED".equals(standing.status())) {
				var heir = this.store.longestAdmin(standing.id());
				this.store.leave(standing.id(), userId);
				if (heir.isPresent()) {
					handOver(standing.id(), null, heir.get(), "system:erasure");
				}
				else {
					closeCommunity(standing.id(), "system:erasure", "owner deleted their account");
				}
			}
			else {
				this.store.leave(standing.id(), userId);
			}
		}
	}

	/** SUPERUSER, by a moderation decision (§18.4). */
	@Transactional
	public void suspend(String moderator, String slug, String reason) {
		CommunityStore.Row row = this.store.find(slug).orElseThrow(() -> new CommunityRefusal(Code.NOT_FOUND, "no such community"));
		if (!"ACTIVE".equals(row.status()) || blank(reason)) {
			throw new CommunityRefusal(Code.WRONG_STATE, "only an active community is taken down, with a reason");
		}
		this.store.setStatus(row.id(), "SUSPENDED", reason.strip(), null);
		this.store.audit(moderator, "COMMUNITY_SUSPENDED", row.id(), Map.of("reason", reason.strip()));
	}

	@Transactional
	public void reinstate(String moderator, String slug) {
		CommunityStore.Row row = this.store.find(slug).orElseThrow(() -> new CommunityRefusal(Code.NOT_FOUND, "no such community"));
		if (!"SUSPENDED".equals(row.status())) {
			throw new CommunityRefusal(Code.WRONG_STATE, "only a suspended community is put back");
		}
		this.store.setStatus(row.id(), "ACTIVE", null, null);
		this.store.audit(moderator, "COMMUNITY_REINSTATED", row.id(), Map.of());
	}

	/** SUPERUSER hands ownership to an ADMIN by a moderation decision (§18.4). */
	@Transactional
	public void transferByModeration(String moderator, String slug, String toUserId) {
		CommunityStore.Row row = this.store.find(slug).orElseThrow(() -> new CommunityRefusal(Code.NOT_FOUND, "no such community"));
		String owner = this.store.members(row.id()).stream().filter((m) -> m.role() == Role.OWNER)
			.map(Views.Member::userId).findFirst().orElse(null);
		handOver(row.id(), owner, toUserId, moderator);
	}

	private Person publisher(Jwt token) {
		Person person = this.people.whoIs(token);
		if (!person.termsUpToDate()) {
			throw new CommunityRefusal(Code.TERMS_REQUIRED, "accept the current terms first");
		}
		if (blank(person.displayName())) {
			throw new CommunityRefusal(Code.DISPLAY_NAME_REQUIRED, "set a display name first");
		}
		return person;
	}

	private void requireRole(CommunityStore.Row row, String userId, boolean manage) {
		Role role = this.store.roleOf(row.id(), userId).orElse(null);
		if (role == null || (manage && !role.manages())) {
			throw new CommunityRefusal(Code.FORBIDDEN, "only an owner or admin may do this");
		}
	}

	private Role member(CommunityStore.Row row, String userId) {
		return this.store.roleOf(row.id(), userId)
			.orElseThrow(() -> new CommunityRefusal(Code.NOT_FOUND, "no such member"));
	}

	private CommunityStore.Row visible(String slug) {
		return this.store.find(slug)
			.filter((row) -> !"SUSPENDED".equals(row.status()))
			.orElseThrow(() -> new CommunityRefusal(Code.NOT_FOUND, "no such community"));
	}

	private CommunityStore.Row active(String slug) {
		CommunityStore.Row row = visible(slug);
		if (!"ACTIVE".equals(row.status())) {
			throw new CommunityRefusal(Code.WRONG_STATE, "the community is closed");
		}
		return row;
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}

}
