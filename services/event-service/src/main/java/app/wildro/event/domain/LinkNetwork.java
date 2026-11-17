package app.wildro.event.domain;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * The network a link belongs to, read off its address. The page draws the four
 * most used ones with their own mark so a visitor recognises them at a glance;
 * anything else is {@link #OTHER} and is shown by its host name.
 * <p>
 * Never stored: an address and a separately kept network name are two facts
 * that could disagree, and the address is the one that is true.
 */
public enum LinkNetwork {

	TELEGRAM("t.me", "telegram.me", "telegram.org", "telegram.dog"),
	INSTAGRAM("instagram.com", "instagr.am"),
	WHATSAPP("whatsapp.com", "wa.me"),
	FACEBOOK("facebook.com", "fb.com", "fb.me"),
	OTHER;

	private final Set<String> domains;

	LinkNetwork(String... domains) {
		this.domains = Set.of(domains);
	}

	/**
	 * Matches the host itself or any subdomain of it, so chat.whatsapp.com and
	 * m.facebook.com are recognised, while a look-alike such as
	 * {@code t.me.example.org} or {@code notfacebook.com} is not.
	 */
	public static LinkNetwork of(String url) {
		String host = URI.create(url).getHost();
		if (host == null) {
			return OTHER;
		}
		String normalised = host.toLowerCase(Locale.ROOT);
		for (LinkNetwork network : values()) {
			for (String domain : network.domains) {
				if (normalised.equals(domain) || normalised.endsWith("." + domain)) {
					return network;
				}
			}
		}
		return OTHER;
	}

}
