package app.wildro.community.community;

/** A role on a membership (SPEC.md §18.3). V1's check constraint mirrors this list. */
public enum Role {

	/** Everything below, plus handing over ownership and closing the community. Exactly one. */
	OWNER,

	/** Writes the community's events, manages who is in it. */
	ADMIN,

	MEMBER;

	public boolean manages() {
		return this == OWNER || this == ADMIN;
	}

}
