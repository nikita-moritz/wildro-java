package app.wildro.event.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A link ends up in an href on a public page, written by a document a language
 * model produced from someone else's text. What it may be is therefore a rule
 * of the domain, not a courtesy of the form.
 */
class EventLinkTest {

	@ParameterizedTest
	@CsvSource({
			"https://t.me/eifel_wandern, TELEGRAM",
			"https://telegram.me/eifel_wandern, TELEGRAM",
			"https://www.instagram.com/eifel.wandern/, INSTAGRAM",
			"https://chat.whatsapp.com/AbCdEfGh, WHATSAPP",
			"https://wa.me/491700000000, WHATSAPP",
			"https://www.facebook.com/events/123, FACEBOOK",
			"https://m.facebook.com/groups/eifel, FACEBOOK",
			"https://fb.me/e/abc, FACEBOOK",
			"https://T.ME/eifel_wandern, TELEGRAM",
			"https://www.komoot.com/tour/1, OTHER",
	})
	void theNetworkIsReadOffTheAddress(String url, LinkNetwork expected) {
		assertThat(new EventLink(LinkScope.EVENT, url).getNetwork()).isEqualTo(expected);
	}

	/** A host that merely contains a network's name is not that network. */
	@ParameterizedTest
	@ValueSource(strings = {
			"https://t.me.example.org/eifel",
			"https://notfacebook.com/eifel",
			"https://example.org/?to=instagram.com",
			"https://whatsapp.com.example.org/x",
	})
	void aLookAlikeHostIsNotRecognised(String url) {
		assertThat(new EventLink(LinkScope.COMMUNITY, url).getNetwork()).isEqualTo(LinkNetwork.OTHER);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"http://t.me/eifel",
			"javascript:alert(1)",
			"data:text/html,<script>alert(1)</script>",
			"//t.me/eifel",
			"t.me/eifel",
			"https:///nohost",
			"https://user:secret@t.me/eifel",
			"https://t.me/with space",
			"   ",
	})
	void anythingButAPlainHttpsAddressIsRefused(String url) {
		assertThatThrownBy(() -> new EventLink(LinkScope.EVENT, url))
				.isInstanceOf(InvalidEventException.class)
				.extracting("field").isEqualTo("url");
	}

	@Test
	void anAddressLongerThanAnyRealOneIsRefused() {
		String url = "https://t.me/" + "a".repeat(EventLink.MAX_LENGTH);

		assertThatThrownBy(() -> new EventLink(LinkScope.EVENT, url)).isInstanceOf(InvalidEventException.class);
	}

	@Test
	void theSameScopeAndAddressIsTheSameLink() {
		assertThat(new EventLink(LinkScope.EVENT, "https://t.me/eifel"))
				.isEqualTo(new EventLink(LinkScope.EVENT, " https://t.me/eifel "))
				.isNotEqualTo(new EventLink(LinkScope.COMMUNITY, "https://t.me/eifel"));
	}

}
