package app.wildro.event.own;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** The same cases as the community service's slugs: the two copies of the rule must not drift. */
class SlugsTest {

	@Test
	void namesBecomeLatinAddresses() {
		assertThat(Slugs.latin("Поход к озеру")).isEqualTo("pokhod-k-ozeru");
		assertThat(Slugs.latin("Sonntagsrunde im Venn")).isEqualTo("sonntagsrunde-im-venn");
		assertThat(Slugs.latin("Wandern für Familien")).isEqualTo("wandern-fuer-familien");
		assertThat(Slugs.forEvent("Hike", LocalDate.of(2026, 11, 25), Set.of("hike-2026-11-25")::contains))
			.isEqualTo("hike-2026-11-25-2");
	}

}
