package app.wildro.event.audit;

/**
 * Who made a change. For the command line that is the named operator plus the
 * account on the server, not a network address (SPEC.md §0.1): on stage one the
 * right to write is access to the machine, so the machine account is the fact
 * that actually identifies someone, and the name is what makes the log readable.
 */
public record Operator(String name, String osUser, AuditChannel channel) {

	public Operator {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("an operator must be named");
		}
		if (osUser == null || osUser.isBlank()) {
			throw new IllegalArgumentException("the server account is required");
		}
		name = name.strip();
	}

	/** The operator of a command-line run, with the account taken from the process. */
	public static Operator cli(String name) {
		return new Operator(name, System.getProperty("user.name"), AuditChannel.CLI);
	}

}
