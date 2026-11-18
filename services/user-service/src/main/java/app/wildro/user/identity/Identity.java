package app.wildro.user.identity;

/**
 * What elvix tells us about a person: the id, the address and the name they gave
 * there. The name may be absent; the address is absent only for an account that
 * no longer exists.
 */
public record Identity(String userId, String email, String displayName) {
}
