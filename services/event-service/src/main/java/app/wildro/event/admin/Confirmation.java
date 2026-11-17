package app.wildro.event.admin;

/**
 * Type-to-confirm for destructive actions: the caller repeats the slug. SPEC.md
 * §0.1 asks for explicit confirmation on cancel and delete; a yes/no flag is what
 * a script, or a model, sets without reading. Having to name the exact event is
 * what makes the confirmation mean something.
 * <p>
 * Lives with the services, not in the command line, so the panel's dialog asks
 * for the same thing and the rule exists once.
 */
final class Confirmation {

	private Confirmation() {
	}

	static void require(String slug, String confirmation) {
		if (!slug.equals(confirmation)) {
			throw new AdminRefusal(AdminRefusal.Code.CONFIRMATION_MISMATCH,
					"confirm by repeating the slug exactly: " + slug);
		}
	}

}
