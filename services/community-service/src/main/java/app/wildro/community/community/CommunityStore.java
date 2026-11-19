package app.wildro.community.community;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import tools.jackson.databind.json.JsonMapper;

/** Every statement of this service. The rules live in {@link CommunityService}. */
@Repository
public class CommunityStore {

	private final JdbcClient jdbc;

	private final JsonMapper json;

	CommunityStore(JdbcClient jdbc, JsonMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	public record Row(long id, String slug, String name, String description, String area, String locale, String status,
			Instant createdAt, Instant closedAt) {
	}

	public Optional<Row> find(String slug) {
		return this.jdbc.sql("""
				SELECT id, slug, name, description, area, locale, status, created_at, closed_at
				FROM communities WHERE slug = :slug
				""").param("slug", slug).query((rs, n) -> new Row(rs.getLong("id"), rs.getString("slug"),
				rs.getString("name"), rs.getString("description"), rs.getString("area"), rs.getString("locale"),
				rs.getString("status"), rs.getTimestamp("created_at").toInstant(),
				rs.getTimestamp("closed_at") == null ? null : rs.getTimestamp("closed_at").toInstant()))
			.optional();
	}

	boolean slugTaken(String slug) {
		return this.jdbc.sql("SELECT EXISTS (SELECT 1 FROM communities WHERE slug = :slug)")
			.param("slug", slug)
			.query(Boolean.class)
			.single();
	}

	long create(String slug, CommunityRequest request) {
		long id = this.jdbc.sql("""
				INSERT INTO communities (slug, name, description, area, locale)
				VALUES (:slug, :name, :description, :area, :locale) RETURNING id
				""")
			.param("slug", slug)
			.param("name", request.name().strip())
			.param("description", request.description().strip())
			.param("area", request.area().strip())
			.param("locale", request.locale())
			.query(Long.class)
			.single();
		replaceLinks(id, request.linkList());
		return id;
	}

	void update(long id, CommunityRequest request) {
		this.jdbc.sql("""
				UPDATE communities SET name = :name, description = :description, area = :area, locale = :locale,
				    updated_at = now()
				WHERE id = :id
				""")
			.param("id", id)
			.param("name", request.name().strip())
			.param("description", request.description().strip())
			.param("area", request.area().strip())
			.param("locale", request.locale())
			.update();
		replaceLinks(id, request.linkList());
	}

	private void replaceLinks(long id, List<String> links) {
		this.jdbc.sql("DELETE FROM community_links WHERE community_id = :id").param("id", id).update();
		for (int i = 0; i < links.size(); i++) {
			this.jdbc.sql("INSERT INTO community_links (community_id, position, url) VALUES (:id, :pos, :url)")
				.param("id", id)
				.param("pos", i)
				.param("url", links.get(i))
				.update();
		}
	}

	List<String> links(long id) {
		return this.jdbc.sql("SELECT url FROM community_links WHERE community_id = :id ORDER BY position")
			.param("id", id)
			.query(String.class)
			.list();
	}

	long memberCount(long id) {
		return this.jdbc.sql("SELECT count(*) FROM memberships WHERE community_id = :id")
			.param("id", id)
			.query(Long.class)
			.single();
	}

	List<Views.Summary> listActive(int page, int size) {
		return this.jdbc.sql("""
				SELECT c.slug, c.name, c.area, c.locale, count(m.user_id) AS members
				FROM communities c LEFT JOIN memberships m ON m.community_id = c.id
				WHERE c.status = 'ACTIVE'
				GROUP BY c.id ORDER BY count(m.user_id) DESC, c.id
				LIMIT :limit OFFSET :offset
				""")
			.param("limit", size + 1)
			.param("offset", page * size)
			.query((rs, n) -> new Views.Summary(rs.getString("slug"), rs.getString("name"), rs.getString("area"),
					rs.getString("locale"), rs.getLong("members")))
			.list();
	}

	Optional<Role> roleOf(long community, String userId) {
		return this.jdbc.sql("SELECT role FROM memberships WHERE community_id = :c AND user_id = :u")
			.param("c", community)
			.param("u", userId)
			.query((rs, n) -> Role.valueOf(rs.getString("role")))
			.optional();
	}

	List<Views.Member> members(long community) {
		return this.jdbc.sql("""
				SELECT user_id, display_name, role, joined_at FROM memberships WHERE community_id = :c
				ORDER BY CASE role WHEN 'OWNER' THEN 0 WHEN 'ADMIN' THEN 1 ELSE 2 END, joined_at
				""")
			.param("c", community)
			.query((rs, n) -> new Views.Member(rs.getString("user_id"), rs.getString("display_name"),
					Role.valueOf(rs.getString("role")), rs.getTimestamp("joined_at").toInstant()))
			.list();
	}

	List<Views.Standing> standingsOf(String userId) {
		return this.jdbc.sql("""
				SELECT c.id, c.slug, c.name, c.status, m.role FROM memberships m JOIN communities c ON c.id = m.community_id
				WHERE m.user_id = :u ORDER BY CASE m.role WHEN 'OWNER' THEN 0 WHEN 'ADMIN' THEN 1 ELSE 2 END, c.name
				""")
			.param("u", userId)
			.query((rs, n) -> new Views.Standing(rs.getLong("id"), rs.getString("slug"), rs.getString("name"),
					rs.getString("status"), Role.valueOf(rs.getString("role"))))
			.list();
	}

	long ownedActive(String userId) {
		return this.jdbc.sql("""
				SELECT count(*) FROM memberships m JOIN communities c ON c.id = m.community_id
				WHERE m.user_id = :u AND m.role = 'OWNER' AND c.status <> 'CLOSED'
				""").param("u", userId).query(Long.class).single();
	}

	void join(long community, String userId, String displayName, Role role, Instant at) {
		this.jdbc.sql("""
				INSERT INTO memberships (community_id, user_id, role, display_name, joined_at, role_since)
				VALUES (:c, :u, :role, :name, :at, :at)
				""")
			.param("c", community)
			.param("u", userId)
			.param("role", role.name())
			.param("name", displayName)
			.param("at", Timestamp.from(at))
			.update();
	}

	/** The name members see follows the person's current one each time they act here. */
	void refreshName(long community, String userId, String displayName) {
		this.jdbc.sql("UPDATE memberships SET display_name = :n WHERE community_id = :c AND user_id = :u AND display_name <> :n")
			.param("n", displayName)
			.param("c", community)
			.param("u", userId)
			.update();
	}

	void leave(long community, String userId) {
		this.jdbc.sql("DELETE FROM memberships WHERE community_id = :c AND user_id = :u")
			.param("c", community)
			.param("u", userId)
			.update();
	}

	void setRole(long community, String userId, Role role, Instant at) {
		this.jdbc.sql("UPDATE memberships SET role = :role, role_since = :at WHERE community_id = :c AND user_id = :u")
			.param("role", role.name())
			.param("at", Timestamp.from(at))
			.param("c", community)
			.param("u", userId)
			.update();
	}

	/**
	 * Communities moderation can act on: active and suspended, the suspended
	 * first, then the newest. {@code query} matches name or address, blank
	 * matches all.
	 */
	List<Views.Moderated> moderated(String query, int limit) {
		String pattern = "%" + query.strip().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
		return this.jdbc.sql("""
				SELECT c.slug, c.name, c.area, c.status, c.suspension_reason, c.created_at, count(m.user_id) AS members
				FROM communities c LEFT JOIN memberships m ON m.community_id = c.id
				WHERE c.status IN ('ACTIVE', 'SUSPENDED')
				  AND (lower(c.name) LIKE :pattern OR c.slug LIKE :pattern)
				GROUP BY c.id
				ORDER BY CASE c.status WHEN 'SUSPENDED' THEN 0 ELSE 1 END, c.created_at DESC, c.id DESC
				LIMIT :limit
				""")
			.param("pattern", pattern)
			.param("limit", limit)
			.query((rs, n) -> new Views.Moderated(rs.getString("slug"), rs.getString("name"), rs.getString("area"),
					rs.getString("status"), rs.getString("suspension_reason"), rs.getLong("members"),
					rs.getTimestamp("created_at").toInstant(), List.of()))
			.list();
	}

	/** The owner and the admins, owner first. */
	List<Views.Member> leaders(String slug) {
		return this.jdbc.sql("""
				SELECT m.user_id, m.display_name, m.role, m.joined_at
				FROM memberships m JOIN communities c ON c.id = m.community_id
				WHERE c.slug = :slug AND m.role IN ('OWNER', 'ADMIN')
				ORDER BY CASE m.role WHEN 'OWNER' THEN 0 ELSE 1 END, m.role_since, m.joined_at
				""")
			.param("slug", slug)
			.query((rs, n) -> new Views.Member(rs.getString("user_id"), rs.getString("display_name"),
					Role.valueOf(rs.getString("role")), rs.getTimestamp("joined_at").toInstant()))
			.list();
	}

	/** The admin who has held the role longest, the heir when the owner's account is deleted (§18.1). */
	Optional<String> longestAdmin(long community) {
		return this.jdbc.sql("""
				SELECT user_id FROM memberships WHERE community_id = :c AND role = 'ADMIN'
				ORDER BY role_since, joined_at LIMIT 1
				""").param("c", community).query(String.class).optional();
	}

	void setStatus(long community, String status, String reason, Instant closedAt) {
		this.jdbc.sql("""
				UPDATE communities SET status = :status, suspension_reason = :reason, closed_at = :closed,
				    updated_at = now()
				WHERE id = :id
				""")
			.param("status", status)
			.param("reason", reason)
			.param("closed", closedAt == null ? null : Timestamp.from(closedAt))
			.param("id", community)
			.update();
	}

	void audit(String actor, String action, long community, Map<String, ?> details) {
		this.jdbc.sql("""
				INSERT INTO audit_log (actor, action, community_id, details) VALUES (:actor, :action, :c, CAST(:d AS jsonb))
				""")
			.param("actor", actor)
			.param("action", action)
			.param("c", community)
			.param("d", details == null || details.isEmpty() ? null : this.json.writeValueAsString(details))
			.update();
	}

	void outbox(String type, long community, Map<String, ?> payload) {
		this.jdbc.sql("INSERT INTO outbox (type, community_id, payload) VALUES (:t, :c, CAST(:p AS jsonb))")
			.param("t", type)
			.param("c", community)
			.param("p", this.json.writeValueAsString(payload))
			.update();
	}

	public List<String> communitiesOf(String userId) {
		return this.jdbc.sql("SELECT c.slug FROM memberships m JOIN communities c ON c.id = m.community_id WHERE m.user_id = :u")
			.param("u", userId)
			.query(String.class)
			.list();
	}

}
