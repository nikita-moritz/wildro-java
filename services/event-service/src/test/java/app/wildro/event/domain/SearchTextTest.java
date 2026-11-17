package app.wildro.event.domain;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SearchTextTest {

	@Test
	void foldsCaseAccentsAndPunctuation() {
		assertThat(SearchText.fold("Wandergesellen Alt-Hürth, „Gut Fuß“ e.V.")).isEqualTo("wandergesellen alt hurth gut fuss e v");
		assertThat(SearchText.fold("Ёлки, Café")).isEqualTo("елки cafe");
	}

	@Test
	void anEventIsFoundByOrganiserNameAndPlaceButNotDescription() {
		String text = SearchText.of("Eifelverein", Map.of(
				"de", new EventTranslation("Laternenwanderung", "Winningen", "Glühwein am Ende."),
				"ru", new EventTranslation("Прогулка с фонарями", "Виннинген", "Глинтвейн.")));
		assertThat(text).isEqualTo("eifelverein laternenwanderung winningen прогулка с фонарями виннинген");
	}

	@Test
	void aQueryBecomesAtMostFiveDistinctWordsOfTwoLettersOrMore() {
		assertThat(SearchText.words("  Hürth a Hürth  ")).containsExactly("hurth");
		assertThat(SearchText.words("one two three four five six")).hasSize(5);
		assertThat(SearchText.words(null)).isEmpty();
	}

}
