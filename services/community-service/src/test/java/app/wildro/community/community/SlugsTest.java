package app.wildro.community.community;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

/** The same cases as the event service's slugs: the two copies of the rule must not drift. */
class SlugsTest {

	@Test
	void namesBecomeLatinAddresses() {
		assertThat(Slugs.latin("Поход к озеру")).isEqualTo("pokhod-k-ozeru");
		assertThat(Slugs.latin("Sonntagsrunde im Venn")).isEqualTo("sonntagsrunde-im-venn");
		assertThat(Slugs.latin("Wandern für Familien")).isEqualTo("wandern-fuer-familien");
		assertThat(Slugs.forCommunity("Trailrunner Aachen", Set.of("trailrunner-aachen")::contains))
			.isEqualTo("trailrunner-aachen-2");
	}

}
