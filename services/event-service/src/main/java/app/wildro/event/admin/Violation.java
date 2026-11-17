package app.wildro.event.admin;

/**
 * One reason a document was refused: where, as a JSON Pointer into the
 * document, and why, in words a model can act on. Machine-readable by design
 * (SPEC.md §0.1): the next attempt must be a correction, not another guess.
 */
public record Violation(String pointer, String reason) {
}
