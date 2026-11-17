package app.wildro.event.cli;

import app.wildro.event.admin.AdminRefusal;
import app.wildro.event.admin.EventCanceller;
import app.wildro.event.admin.EventDeleter;
import app.wildro.event.admin.EventImporter;
import app.wildro.event.admin.EventLister;
import app.wildro.event.admin.EventSummary;
import app.wildro.event.admin.ImportOutcome;
import app.wildro.event.admin.ImportOutcome.Action;
import app.wildro.event.admin.Violation;
import app.wildro.event.audit.Operator;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The commands behind {@code events ...}. A thin shell by requirement (SPEC.md
 * §0.1): parse, call a service, print the result. No SQL here and no rule of its
 * own, so the panel, when it arrives, calls the same services and cannot drift.
 * <p>
 * Everything a caller needs goes to stdout as one JSON object, because the
 * caller is often a language model that acts on the answer. Logs go to stderr so
 * they never corrupt it.
 */
@Component
public class EventCommands {

	static final int OK = 0;

	/** The input or the request was refused; nothing was written. */
	static final int REFUSED = 1;

	static final int USAGE = 2;

	static final int FAILURE = 3;

	static final int DEFAULT_PAGE_SIZE = 50;

	private final EventImporter importer;

	private final EventCanceller canceller;

	private final EventDeleter deleter;

	private final EventLister lister;

	private final JsonMapper json;

	EventCommands(EventImporter importer, EventCanceller canceller, EventDeleter deleter, EventLister lister,
			JsonMapper json) {
		this.importer = importer;
		this.canceller = canceller;
		this.deleter = deleter;
		this.lister = lister;
		this.json = json;
	}

	int execute(CliRequest request, InputStream stdin, PrintStream out) {
		try {
			return switch (request.command()) {
				case CliRequest.IMPORT -> importDocument(request, stdin, out);
				case CliRequest.CANCEL -> cancel(request, out);
				case CliRequest.DELETE -> delete(request, out);
				case CliRequest.LIST -> list(request, out);
				default -> throw new CliRequest.UsageException("unknown command: " + request.command());
			};
		}
		catch (AdminRefusal refusal) {
			print(out, error(request.command(), refusal.getCode().name(), refusal.getMessage()));
			return REFUSED;
		}
		catch (CliRequest.UsageException | IllegalArgumentException ex) {
			print(out, error(request.command(), "USAGE", ex.getMessage()));
			return USAGE;
		}
	}

	private int importDocument(CliRequest request, InputStream stdin, PrintStream out) {
		Operator operator = Operator.cli(request.options().get("operator"));
		boolean write = request.switches().contains("write");

		JsonNode document;
		try {
			document = this.json.readTree(read(request.target(), stdin));
		}
		catch (JacksonException ex) {
			ImportOutcome.Mode mode = write ? ImportOutcome.Mode.WRITE : ImportOutcome.Mode.DRY_RUN;
			print(out, importResult(new ImportOutcome(mode,
					List.of(new Violation("/", "not valid JSON: " + ex.getOriginalMessage())), List.of())));
			return REFUSED;
		}

		ImportOutcome outcome = write ? this.importer.apply(document, operator) : this.importer.plan(document);
		print(out, importResult(outcome));
		return outcome.ok() ? OK : REFUSED;
	}

	private int cancel(CliRequest request, PrintStream out) {
		boolean changed = this.canceller.cancel(request.target(), request.options().get("confirm"),
				Operator.cli(request.options().get("operator")));
		Map<String, Object> result = ok(CliRequest.CANCEL);
		result.put("slug", request.target());
		result.put("changed", changed);
		print(out, result);
		return OK;
	}

	private int delete(CliRequest request, PrintStream out) {
		this.deleter.delete(request.target(), request.options().get("confirm"), request.options().get("reason"),
				Operator.cli(request.options().get("operator")));
		Map<String, Object> result = ok(CliRequest.DELETE);
		result.put("slug", request.target());
		print(out, result);
		return OK;
	}

	private int list(CliRequest request, PrintStream out) {
		boolean all = request.switches().contains("all");
		int page = request.intOption("page", 0);
		int size = request.intOption("size", DEFAULT_PAGE_SIZE);
		Slice<EventSummary> slice = this.lister.list(all, page, size);

		Map<String, Object> result = ok(CliRequest.LIST);
		result.put("all", all);
		result.put("page", page);
		result.put("size", size);
		result.put("hasNext", slice.hasNext());
		result.put("events", slice.getContent().stream().map(EventCommands::summary).toList());
		print(out, result);
		return OK;
	}

	private Map<String, Object> importResult(ImportOutcome outcome) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("command", CliRequest.IMPORT);
		result.put("mode", outcome.mode().name());
		result.put("ok", outcome.ok());
		if (outcome.ok()) {
			Map<String, Object> summary = new LinkedHashMap<>();
			summary.put("create", outcome.count(Action.CREATE));
			summary.put("update", outcome.count(Action.UPDATE));
			summary.put("unchanged", outcome.count(Action.UNCHANGED));
			result.put("summary", summary);
			result.put("items", outcome.items());
			if (outcome.mode() == ImportOutcome.Mode.DRY_RUN) {
				result.put("note", "dry run: nothing was written. Repeat with --write to apply.");
			}
		}
		result.put("violations", outcome.violations());
		return result;
	}

	private static Map<String, Object> summary(EventSummary event) {
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("slug", event.slug());
		line.put("status", event.status().name());
		line.put("activityType", event.activityType().name());
		line.put("startsAt", event.startsAt().toString());
		line.put("passesAt", event.passesAt().toString());
		line.put("nameDe", event.name());
		return line;
	}

	static Map<String, Object> error(String command, String code, String message) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("command", command);
		result.put("ok", false);
		result.put("error", Map.of("code", code, "message", message == null ? "" : message));
		return result;
	}

	private static Map<String, Object> ok(String command) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("command", command);
		result.put("ok", true);
		return result;
	}

	private void print(PrintStream out, Map<String, Object> result) {
		out.println(this.json.writerWithDefaultPrettyPrinter().writeValueAsString(result));
		out.flush();
	}

	private static String read(String target, InputStream stdin) {
		try {
			if ("-".equals(target)) {
				return new String(stdin.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
			}
			return Files.readString(Path.of(target));
		}
		catch (NoSuchFileException ex) {
			throw new CliRequest.UsageException("no such file: " + target);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("cannot read " + target, ex);
		}
	}

}
