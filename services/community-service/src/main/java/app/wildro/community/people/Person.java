package app.wildro.community.people;

/** The person behind a token, as the user service knows them. */
public record Person(String id, String displayName, boolean termsUpToDate) {
}
