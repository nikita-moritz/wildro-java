package app.wildro.event.cli;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A parsed command line. Unknown commands and unknown flags are refused rather
 * than ignored, for the same reason the import schema refuses unknown keys: a
 * mistyped {@code --wirte} that silently did nothing would turn a real run into a
 * dry run and nobody would notice.
 */
record CliRequest(String command, String target, Map<String, String> options, Set<String> switches) {

	static final String IMPORT = "import";

	static final String CANCEL = "cancel";

	static final String DELETE = "delete";

	static final String LIST = "list";

	static final String HELP = "help";

	private static final Map<String, Spec> SPECS = Map.of(
			IMPORT, new Spec(true, Set.of("operator"), Set.of(), Set.of("write")),
			CANCEL, new Spec(true, Set.of("operator", "confirm"), Set.of(), Set.of()),
			DELETE, new Spec(true, Set.of("operator", "confirm", "reason"), Set.of(), Set.of()),
			LIST, new Spec(false, Set.of(), Set.of("page", "size"), Set.of("all")),
			HELP, new Spec(false, Set.of(), Set.of(), Set.of()));

	static CliRequest parse(List<String> args) {
		if (args.isEmpty()) {
			throw new UsageException("no command given");
		}
		String command = args.getFirst();
		Spec spec = SPECS.get(command);
		if (spec == null) {
			throw new UsageException("unknown command: " + command);
		}

		String target = null;
		Map<String, String> options = new HashMap<>();
		Set<String> switches = new HashSet<>();
		for (int i = 1; i < args.size(); i++) {
			String arg = args.get(i);
			if (!arg.startsWith("--")) {
				if (!spec.takesTarget() || target != null) {
					throw new UsageException("unexpected argument: " + arg);
				}
				target = arg;
				continue;
			}
			String name = arg.substring(2);
			if (spec.switches().contains(name)) {
				switches.add(name);
			}
			else if (spec.required().contains(name) || spec.optional().contains(name)) {
				if (i + 1 >= args.size() || args.get(i + 1).startsWith("--")) {
					throw new UsageException("--" + name + " needs a value");
				}
				options.put(name, args.get(++i));
			}
			else {
				throw new UsageException("unknown option for " + command + ": " + arg);
			}
		}

		if (spec.takesTarget() && target == null) {
			throw new UsageException(command + " needs " + (IMPORT.equals(command) ? "a file, or - for stdin" : "a slug"));
		}
		for (String required : spec.required()) {
			if (!options.containsKey(required)) {
				throw new UsageException(command + " needs --" + required);
			}
		}
		return new CliRequest(command, target, Map.copyOf(options), Set.copyOf(switches));
	}

	int intOption(String name, int fallback) {
		String value = this.options.get(name);
		if (value == null) {
			return fallback;
		}
		try {
			return Integer.parseInt(value);
		}
		catch (NumberFormatException ex) {
			throw new UsageException("--" + name + " must be a whole number, got " + value);
		}
	}

	private record Spec(boolean takesTarget, Set<String> required, Set<String> optional, Set<String> switches) {
	}

	static final class UsageException extends RuntimeException {

		UsageException(String message) {
			super(message);
		}

	}

}
