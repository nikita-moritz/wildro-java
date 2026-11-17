package app.wildro.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

/**
 * The attribution a cover photo must carry: who made it, the licence, where the
 * licence is written, and where the original lives. A free licence such as
 * CC BY-SA allows the photo on a commercial site only with these shown beside
 * it, so an event's cover never exists without them (V5 holds the same rule).
 */
@Embeddable
public class CoverCredit {

	@Column(name = "cover_author")
	private String author;

	@Column(name = "cover_license")
	private String license;

	@Column(name = "cover_license_url")
	private String licenseUrl;

	@Column(name = "cover_source_url")
	private String sourceUrl;

	protected CoverCredit() {
		// for JPA
	}

	/** @throws InvalidEventException naming the field of the cover credit that is wrong. */
	public CoverCredit(String author, String license, String licenseUrl, String sourceUrl) {
		this.author = text(author, "author", 300);
		this.license = text(license, "license", 100);
		this.licenseUrl = https(licenseUrl, "licenseUrl");
		this.sourceUrl = https(sourceUrl, "sourceUrl");
	}

	private static String text(String value, String field, int max) {
		if (value == null || value.isBlank()) {
			throw new InvalidEventException(field, "is required and must not be blank");
		}
		String stripped = value.strip();
		if (stripped.length() > max) {
			throw new InvalidEventException(field, "must be at most " + max + " characters");
		}
		return stripped;
	}

	private static String https(String value, String field) {
		String url = text(value, field, 2000);
		if (!url.startsWith("https://")) {
			throw new InvalidEventException(field, "must be an https address");
		}
		return url;
	}

	public String getAuthor() {
		return this.author;
	}

	public String getLicense() {
		return this.license;
	}

	public String getLicenseUrl() {
		return this.licenseUrl;
	}

	public String getSourceUrl() {
		return this.sourceUrl;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof CoverCredit that && Objects.equals(this.author, that.author)
				&& Objects.equals(this.license, that.license) && Objects.equals(this.licenseUrl, that.licenseUrl)
				&& Objects.equals(this.sourceUrl, that.sourceUrl);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.author, this.license, this.licenseUrl, this.sourceUrl);
	}

}
