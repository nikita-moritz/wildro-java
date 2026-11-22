package app.wildro.community.moderation;

/**
 * Why someone reports a community: the same list as for events, so the site
 * shows one form. V3's check constraint mirrors it, held by
 * ReportReasonMatchesTheSchemaTest.
 */
public enum ReportReason {

	SPAM,
	MISLEADING,
	DANGEROUS,
	ILLEGAL,
	PERSONAL_DATA,
	OTHER

}
