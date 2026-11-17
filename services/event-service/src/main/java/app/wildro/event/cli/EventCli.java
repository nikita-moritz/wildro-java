package app.wildro.event.cli;

import app.wildro.event.EventServiceApplication;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.io.PrintStream;
import java.util.Arrays;

/**
 * Starts the service as a one-shot command instead of a web server:
 * {@code java -jar event-service.jar events <command> ...}.
 * <p>
 * Same jar, same services, same validation and same database settings as the
 * running service, which is the point: the command is a thin shell over them.
 * No web server starts, so it cannot collide with the running instance's port,
 * and there is still no network path that writes (SPEC.md §0.1).
 */
public final class EventCli {

	/** The first argument that switches the jar into command mode. */
	public static final String COMMAND = "events";

	/** Profile with command-mode settings: logs on stderr, migrations left to the deploy. */
	static final String PROFILE = "cli";

	static final String USAGE = """
			usage: events <command> [arguments]

			  import <file | ->  --operator NAME [--write]
			      Create or revise events from a JSON document (schema:
			      import/event-import.schema.json, guide: docs/event-import.md).
			      Without --write it is a dry run and writes nothing.

			  cancel <slug>  --operator NAME --confirm <slug>
			      Mark an event cancelled. Repeat the slug to confirm.

			  delete <slug>  --operator NAME --confirm <slug> --reason TEXT
			      Remove an event and its address. For junk and mistakes only.

			  list  [--all] [--page N] [--size N]
			      What has not passed yet; --all adds past and cancelled events.

			Output is one JSON object on stdout. Exit codes: 0 ok, 1 refused,
			nothing written; 2 usage; 3 unexpected failure.
			""";

	private EventCli() {
	}

	public static int run(String[] args) {
		PrintStream out = System.out;
		CliRequest request;
		try {
			request = CliRequest.parse(Arrays.asList(args));
		}
		catch (CliRequest.UsageException ex) {
			System.err.print(USAGE);
			out.println(JsonMapper.builder().build().writeValueAsString(
					EventCommands.error(args.length > 0 ? args[0] : "", "USAGE", ex.getMessage())));
			return EventCommands.USAGE;
		}
		if (CliRequest.HELP.equals(request.command())) {
			out.print(USAGE);
			return EventCommands.OK;
		}

		SpringApplication application = new SpringApplication(EventServiceApplication.class);
		application.setWebApplicationType(WebApplicationType.NONE);
		application.setBannerMode(Banner.Mode.OFF);
		application.setAdditionalProfiles(PROFILE);
		// No arguments are handed to Spring: ours are not property overrides, and
		// "--operator nikita" would otherwise become a property named operator.
		try (ConfigurableApplicationContext context = application.run()) {
			return context.getBean(EventCommands.class).execute(request, System.in, out);
		}
		catch (RuntimeException ex) {
			ex.printStackTrace(System.err);
			out.println(JsonMapper.builder().build().writeValueAsString(
					EventCommands.error(request.command(), "FAILURE", String.valueOf(ex.getMessage()))));
			return EventCommands.FAILURE;
		}
	}

}
