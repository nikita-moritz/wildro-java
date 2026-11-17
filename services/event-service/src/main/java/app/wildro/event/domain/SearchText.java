package app.wildro.event.domain;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The one definition of how text is folded for search (SPEC.md §0, поиск):
 * lower case, umlauts and accents dropped, ß as ss, ё as е, everything that is
 * not a letter or a digit a single space. Both sides go through it, the stored
 * text of an event and what a visitor types, so "Hurth" finds "Hürth" and
 * "Монреаль" finds the Russian name of Monreal.
 * <p>
 * It lives in the domain, beside {@code Event#computePassesAt}, for the same
 * reason: the column {@code search_text} stores its output and is never written
 * any other way, so the rule cannot drift between Java and SQL.
 */
public final class SearchText {

	/** Longest query taken from a visitor; anything after it is dropped. */
	public static final int MAX_QUERY_LENGTH = 100;

	/** At most this many words of a query are used: each one is a predicate. */
	public static final int MAX_WORDS = 5;

	private static final Pattern MARKS = Pattern.compile("\\p{M}+");

	private static final Pattern NOT_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

	private SearchText() {
	}

	public static String fold(String text) {
		if (text == null) {
			return "";
		}
		String lower = text.toLowerCase(Locale.ROOT).replace("ß", "ss").replace("ё", "е");
		String bare = MARKS.matcher(Normalizer.normalize(lower, Normalizer.Form.NFD)).replaceAll("");
		return NOT_WORD.matcher(bare).replaceAll(" ").strip();
	}

	/** What an event is found by: organiser, and name and place in every language. Never the description. */
	static String of(String organizer, Map<String, EventTranslation> translations) {
		Stream<String> parts = Stream.concat(Stream.of(organizer),
				translations.keySet().stream().sorted().map(translations::get)
						.flatMap(t -> Stream.of(t.getName(), t.getPlaceName())));
		return fold(String.join(" ", parts.toList()));
	}

	/**
	 * A visitor's query as the words to match, each folded. Single letters are
	 * dropped: they match nearly everything and say nothing.
	 */
	public static List<String> words(String query) {
		if (query == null) {
			return List.of();
		}
		String cut = query.length() > MAX_QUERY_LENGTH ? query.substring(0, MAX_QUERY_LENGTH) : query;
		return Arrays.stream(fold(cut).split(" ")).filter(w -> w.length() > 1).distinct().limit(MAX_WORDS).toList();
	}

}
