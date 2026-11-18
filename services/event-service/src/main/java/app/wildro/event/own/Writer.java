package app.wildro.event.own;

/**
 * The person behind a token, as the user service knows them: the name they
 * appear under and whether they accepted the terms in force.
 */
public record Writer(String id, String displayName, boolean termsUpToDate) {
}
