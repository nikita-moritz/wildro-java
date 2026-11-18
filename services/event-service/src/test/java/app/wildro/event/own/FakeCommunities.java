package app.wildro.event.own;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.security.oauth2.jwt.Jwt;

/** The community service in memory: per community, who holds which role. */
public class FakeCommunities implements CommunityGateway {

	private final Map<String, Standing> communities = new ConcurrentHashMap<>();

	private final Map<String, String> roles = new ConcurrentHashMap<>();

	public void community(long id, String slug, String name, String status) {
		this.communities.put(slug, new Standing(id, slug, name, status, null));
	}

	public void role(String slug, String userId, String role) {
		this.roles.put(slug + "/" + userId, role);
	}

	public void reset() {
		this.communities.clear();
		this.roles.clear();
	}

	@Override
	public Standing standing(Jwt token, String slug) {
		Standing community = this.communities.get(slug);
		return community == null ? null
				: new Standing(community.id(), slug, community.name(), community.status(),
						this.roles.get(slug + "/" + token.getSubject()));
	}

}
