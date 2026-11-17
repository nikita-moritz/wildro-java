package app.wildro.event;

import app.wildro.event.cli.EventCli;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Arrays;

@SpringBootApplication
public class EventServiceApplication {

	/**
	 * Runs the web service, or with {@code events ...} as the arguments, one admin
	 * command and exits. The systemd unit passes no arguments.
	 */
	public static void main(String[] args) {
		if (args.length > 0 && EventCli.COMMAND.equals(args[0])) {
			System.exit(EventCli.run(Arrays.copyOfRange(args, 1, args.length)));
		}
		SpringApplication.run(EventServiceApplication.class, args);
	}

}
