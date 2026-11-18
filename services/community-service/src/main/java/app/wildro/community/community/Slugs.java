package app.wildro.community.community;

import java.text.Normalizer;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The address of a community: its name in Latin letters. The same rule as the
 * event service's Slugs, kept in step by the same cases in both test suites.
 * Russian names are transliterated, German umlauts written out, so the address
 * reads the same in every language (paths are English, house rule).
 */
final class Slugs {

	private static final int MAX_NAME = 60;

	private static final Map<Character, String> CYRILLIC = Map.ofEntries(Map.entry('а', "a"), Map.entry('б', "b"),
			Map.entry('в', "v"), Map.entry('г', "g"), Map.entry('д', "d"), Map.entry('е', "e"), Map.entry('ё', "e"),
			Map.entry('ж', "zh"), Map.entry('з', "z"), Map.entry('и', "i"), Map.entry('й', "y"), Map.entry('к', "k"),
			Map.entry('л', "l"), Map.entry('м', "m"), Map.entry('н', "n"), Map.entry('о', "o"), Map.entry('п', "p"),
			Map.entry('р', "r"), Map.entry('с', "s"), Map.entry('т', "t"), Map.entry('у', "u"), Map.entry('ф', "f"),
			Map.entry('х', "kh"), Map.entry('ц', "ts"), Map.entry('ч', "ch"), Map.entry('ш', "sh"),
			Map.entry('щ', "shch"), Map.entry('ъ', ""), Map.entry('ы', "y"), Map.entry('ь', ""), Map.entry('э', "e"),
			Map.entry('ю', "yu"), Map.entry('я', "ya"), Map.entry('і', "i"), Map.entry('ї', "yi"), Map.entry('є', "ye"),
			Map.entry('ґ', "g"), Map.entry('ä', "ae"), Map.entry('ö', "oe"), Map.entry('ü', "ue"), Map.entry('ß', "ss"));

	private Slugs() {
	}

	/** A free address for this name; {@code taken} says which are in use. */
	static String forCommunity(String name, Predicate<String> taken) {
		String base = latin(name);
		if (base.isEmpty()) {
			base = "community";
		}
		String candidate = base;
		for (int n = 2; taken.test(candidate); n++) {
			candidate = base + "-" + n;
		}
		return candidate;
	}

	static String latin(String text) {
		StringBuilder out = new StringBuilder();
		for (char c : text.toLowerCase().toCharArray()) {
			String mapped = CYRILLIC.get(c);
			out.append(mapped != null ? mapped : String.valueOf(c));
		}
		String ascii = Normalizer.normalize(out, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
		String slug = ascii.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
		if (slug.length() > MAX_NAME) {
			slug = slug.substring(0, MAX_NAME).replaceAll("-+$", "");
		}
		return slug;
	}

}
