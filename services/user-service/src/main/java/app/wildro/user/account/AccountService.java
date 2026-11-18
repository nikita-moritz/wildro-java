package app.wildro.user.account;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.wildro.user.identity.Identity;
import app.wildro.user.identity.IdentityDirectory;

/** The account rules of SPEC.md §18.1. Every method takes the subject of a verified token. */
@Service
public class AccountService {

	private final AccountRepository accounts;

	private final IdentityDirectory directory;

	private final TermsProperties terms;

	private final Clock clock;

	AccountService(AccountRepository accounts, IdentityDirectory directory, TermsProperties terms, Clock clock) {
		this.accounts = accounts;
		this.directory = directory;
		this.terms = terms;
		this.clock = clock;
	}

	/**
	 * The person behind the token. The first time they arrive, or the first time
	 * after elvix restored an account we had erased, the row is filled from elvix.
	 * elvix's name becomes the starting display name when it passes our rule.
	 */
	@Transactional
	public Account current(String userId) {
		var existing = this.accounts.find(userId);
		if (existing.isPresent() && !existing.get().erased()) {
			return existing.get();
		}
		Identity identity = this.directory.find(userId).orElseThrow(() -> new UnknownPersonException(userId));
		this.accounts.fill(identity, DisplayName.normalise(identity.displayName()));
		return this.accounts.find(userId).orElseThrow();
	}

	@Transactional
	public Account rename(String userId, String requested) {
		String name = DisplayName.normalise(requested);
		if (name == null) {
			throw new InvalidDisplayNameException();
		}
		current(userId);
		this.accounts.rename(userId, name);
		return this.accounts.find(userId).orElseThrow();
	}

	/**
	 * Records acceptance of the version in force. The caller names the version
	 * the person was shown, so terms that changed between rendering the page and
	 * pressing the button are not accepted on their behalf.
	 */
	@Transactional
	public Account acceptTerms(String userId, String shownVersion) {
		if (!this.terms.published()) {
			throw new TermsNotPublishedException();
		}
		if (!this.terms.version().equals(shownVersion)) {
			throw new StaleTermsException(this.terms.version());
		}
		current(userId);
		this.accounts.acceptTerms(userId, this.terms.version(), this.clock.instant());
		return this.accounts.find(userId).orElseThrow();
	}

	public TermsProperties terms() {
		return this.terms;
	}

	/** elvix does not know the subject of a token it signed: deleted between minting and now. */
	public static class UnknownPersonException extends RuntimeException {

		UnknownPersonException(String userId) {
			super("elvix does not know " + userId);
		}

	}

	public static class InvalidDisplayNameException extends RuntimeException {

	}

	public static class TermsNotPublishedException extends RuntimeException {

	}

	public static class StaleTermsException extends RuntimeException {

		private final String current;

		StaleTermsException(String current) {
			this.current = current;
		}

		public String current() {
			return this.current;
		}

	}

}
