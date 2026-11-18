package app.wildro.user.account;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param version the terms version currently in force, blank while no terms
 *        page is published
 */
@ConfigurationProperties("wildro.terms")
public record TermsProperties(String version) {

	public boolean published() {
		return this.version != null && !this.version.isBlank();
	}

}
