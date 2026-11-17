package app.wildro.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

/**
 * One link beside an event: where it leads and what it is about.
 * <p>
 * A value object, like {@link EventTranslation}: two links with the same scope
 * and address are the same link, which is what lets a repeated import see that
 * nothing changed.
 */
@Embeddable
public class EventLink {

	/** Longer than any real profile or invite address, short enough to refuse an abuse of the field. */
	static final int MAX_LENGTH = 500;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private LinkScope scope;

	@Column(nullable = false)
	private String url;

	protected EventLink() {
		// for JPA
	}

	/**
	 * @throws InvalidEventException with the field {@code url} when the address is
	 * not a plain https link. The caller knows where in the document it stood.
	 */
	public EventLink(LinkScope scope, String url) {
		this.scope = Objects.requireNonNull(scope, "scope is required");
		this.url = requireHttps(url);
	}

	/**
	 * https only, and with a host. The page puts this address into an href, so a
	 * {@code javascript:} or {@code data:} one must never get as far as the table.
	 */
	private static String requireHttps(String value) {
		if (value == null || value.isBlank()) {
			throw new InvalidEventException("url", "is required and must not be blank");
		}
		String url = value.strip();
		if (url.length() > MAX_LENGTH) {
			throw new InvalidEventException("url", "must be at most " + MAX_LENGTH + " characters");
		}
		URI uri;
		try {
			uri = new URI(url);
		}
		catch (URISyntaxException ex) {
			throw new InvalidEventException("url", "is not a valid address");
		}
		if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
			throw new InvalidEventException("url", "must be an https address with a host and no credentials");
		}
		return url;
	}

	public LinkScope getScope() {
		return this.scope;
	}

	public String getUrl() {
		return this.url;
	}

	public LinkNetwork getNetwork() {
		return LinkNetwork.of(this.url);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof EventLink that && this.scope == that.scope && this.url.equals(that.url);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.scope, this.url);
	}

	@Override
	public String toString() {
		return this.scope + " " + this.url;
	}

}
