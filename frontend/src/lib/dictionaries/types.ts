import type { ActivityType, Difficulty } from "@/lib/vocabulary";

/**
 * Every string the interface shows, in one shape. Each language file is typed
 * against it, so a string added in one language and forgotten in another is a
 * compile error, not a hole on a page. Event content (names, descriptions) is not
 * here: it comes from the event service, already in the page's language.
 *
 * The copy follows the working version of wildro, reworded only where it
 * promised what stage one does not have: accounts, joining, creating events,
 * communities, counted places.
 */
export interface Dictionary {
  site: {
    name: string;
    tagline: string;
    /** The site-wide default, and the home page's description. */
    description: string;
    /**
     * The home page's <title> without the brand: topic and region first, for
     * search (niki seo-check: at most 60 characters with " · wildro", keyword
     * in the first four words).
     */
    homeTitle: string;
  };
  nav: {
    map: string;
    events: string;
    home: string;
    language: string;
    menu: string;
    back: string;
    account: string;
    communities: string;
    theme: { toggle: string; light: string; dark: string; system: string };
  };
  home: {
    kicker: string;
    title1: string;
    title2: string;
    subtitle: string;
    primaryCta: string;
    upcomingTitle: string;
    upcomingSubtitle: string;
    upcomingCta: string;
    featuresTitle: string;
    featuresSubtitle: string;
    features: ReadonlyArray<{ title: string; description: string }>;
    emptyTitle: string;
    emptySubtitle: string;
  };
  feed: {
    title: string;
    create: string;
    description: string;
    empty: string;
    filteredEmpty: string;
    search: { label: string; placeholder: string; clear: string };
    /** The list's <title> on page one, topic and region first; `title` is the visible heading. */
    metaTitle: string;
    pageTitle: (page: number) => string;
    /** Link to the page before, for whoever arrives on a later page. */
    previous: string;
    /** Link to the page after, shown only without JavaScript. */
    next: string;
    view: { label: string; list: string; tiles: string };
    loading: string;
    loadFailed: string;
    retry: string;
  };
  filters: {
    button: string;
    title: string;
    activity: string;
    difficulty: string;
    price: string;
    priceAny: string;
    dates: string;
    dateFrom: string;
    dateTo: string;
    datePlaceholder: string;
    dateClear: string;
    distance: string;
    distanceMin: string;
    distanceMax: string;
    nearMe: string;
    nearMeHint: string;
    radius: string;
    locating: string;
    locationDenied: string;
    apply: string;
    reset: string;
    close: string;
  };
  map: {
    title: string;
    description: string;
    emptyView: string;
    tooMany: (shown: number) => string;
    openEvent: string;
    /** Carries {count}: the text crosses into a client component, where a function cannot go. */
    inView: string;
    listLabel: string;
    search: string;
    noMatch: string;
    unavailable: string;
    consentTitle: string;
    consentBody: string;
    consentButton: string;
    consentPrivacyLink: string;
  };
  event: {
    back: string;
    description: string;
    type: string;
    startTime: string;
    until: string;
    where: string;
    organizer: string;
    distance: string;
    elevation: string;
    difficulty: string;
    openMap: string;
    eventLinks: string;
    communityLinks: string;
    stockPhoto: string;
    /** The same note on a tile's photo, where there is room for a word or two. */
    stockPhotoShort: string;
    photo: string;
    photoSource: string;
    photoPlace: string;
    sourceNotice: string;
    lastChecked: (date: string) => string;
    toSource: string;
    cancelled: string;
    past: string;
    upcomingInstead: string;
    ownBadge: string;
    sourceBadge: string;
    inOrganizerLanguage: string;
    organizerErased: string;
    kilometres: (value: string) => string;
    metres: (value: string) => string;
  };
  activity: Record<ActivityType, string>;
  difficulty: Record<Difficulty, string>;
  /** The badge on a card and a page, and the filter's choices. */
  price: { free: string; paid: string };
  /** The offer to add wildro to a phone's home screen. */
  install: { title: string; body: string; iosSteps: [string, string, string]; install: string; gotIt: string; close: string };
  footer: {
    legalNotice: string;
    privacy: string;
    terms: string;
    rights: string;
    builtBy: string;
  };
  legal: {
    legalNoticeTitle: string;
    privacyTitle: string;
    /** Shown above the legal text in a language it is not written in; null in German. */
    germanOnly: string | null;
  };
  notFound: {
    title: string;
    body: string;
    back: string;
  };
  error: {
    title: string;
    body: string;
    retry: string;
  };
  auth: {
    signInTitle: string;
    signInLead: string;
    unavailable: string;
    accountTitle: string;
    displayName: string;
    displayNameHint: string;
    displayNameMissing: string;
    save: string;
    saved: string;
    invalidName: string;
    email: string;
    emailNote: string;
    roles: string;
    signOut: string;
    sessionsTitle: string;
    sessionsLead: string;
    leaveTitle: string;
    leaveLead: string;
    failed: string;
  };
  own: {
    myEventsTitle: string;
    myEventsLead: string;
    newEvent: string;
    empty: string;
    statusDRAFT: string;
    statusPUBLISHED: string;
    statusCANCELLED: string;
    statusSUSPENDED: string;
    suspendedBecause: string;
    edit: string;
    editorNew: string;
    editorEdit: string;
    fieldLanguage: string;
    fieldLanguageHint: string;
    fieldName: string;
    fieldPlace: string;
    fieldDescription: string;
    fieldActivity: string;
    fieldDifficulty: string;
    difficultyNone: string;
    fieldStarts: string;
    fieldEnds: string;
    fieldTime: string;
    fieldLocation: string;
    fieldLocationHint: string;
    latitude: string;
    longitude: string;
    useMyLocation: string;
    pickOnMap: string;
    fieldDistance: string;
    fieldElevation: string;
    fieldCapacity: string;
    fieldEventLinks: string;
    fieldCommunityLinks: string;
    linksHint: string;
    saveDraft: string;
    saveAndPublish: string;
    save: string;
    publish: string;
    cancelEvent: string;
    saved: string;
    published: string;
    cancelledDone: string;
    termsTitle: string;
    termsNeeded: string;
    termsAccept: string;
    termsAccepted: string;
    termsRead: string;
    moderationLink: string;
    ownerLabel: string;
    ownerMe: string;
    viewPublic: string;
    back: string;
    errors: Record<"TERMS_REQUIRED" | "DISPLAY_NAME_REQUIRED" | "LIMIT_PER_DAY" | "LIMIT_UPCOMING" | "IN_THE_PAST" | "WRONG_STATE" | "NOT_YOURS" | "NOT_FOUND" | "INVALID" | "UNAVAILABLE" | "GENERIC", string>;
  };
  moderation: {
    title: string;
    lead: string;
    empty: string;
    reason: string;
    reportedAt: string;
    suspend: string;
    suspendReason: string;
    dismiss: string;
    reinstate: string;
    view: string;
    done: string;
    tabReports: string;
    tabCommunities: string;
    communitiesLead: string;
    communitiesEmpty: string;
    search: string;
    searchButton: string;
    suspendedBadge: string;
    communityReason: string;
    transferTo: string;
    transfer: string;
    transferHint: string;
    noAdmins: string;
    logoReason: string;
    logoRemove: string;
  };
  report: {
    open: string;
    title: string;
    openCommunity: string;
    titleCommunity: string;
    reason: string;
    details: string;
    send: string;
    sent: string;
    tooMany: string;
    failed: string;
    reasons: Record<"SPAM" | "MISLEADING" | "DANGEROUS" | "ILLEGAL" | "PERSONAL_DATA" | "OTHER", string>;
  };
  community: {
    listTitle: string;
    listLead: string;
    listEmpty: string;
    members: string;
    area: string;
    closed: string;
    upcoming: string;
    noUpcoming: string;
    links: string;
    join: string;
    leave: string;
    joined: string;
    manage: string;
    signInToJoin: string;
    membersTitle: string;
    membersOnly: string;
    roleOWNER: string;
    roleADMIN: string;
    roleMEMBER: string;
    makeAdmin: string;
    makeMember: string;
    remove: string;
    transfer: string;
    transferHint: string;
    close: string;
    closeConfirm: string;
    myTitle: string;
    myLead: string;
    myEmpty: string;
    newCommunity: string;
    editorNew: string;
    editorEdit: string;
    fieldName: string;
    fieldDescription: string;
    fieldArea: string;
    fieldLanguage: string;
    fieldLinks: string;
    linksHint: string;
    create: string;
    save: string;
    saved: string;
    done: string;
    back: string;
    errors: Record<"NOT_FOUND" | "FORBIDDEN" | "TERMS_REQUIRED" | "DISPLAY_NAME_REQUIRED" | "LIMIT_OWNED" | "INVALID" | "WRONG_STATE" | "OWNER_CANNOT_LEAVE" | "TARGET_NOT_ADMIN" | "NO_FILE" | "TOO_LARGE" | "NOT_AN_IMAGE" | "UNAVAILABLE" | "GENERIC", string>;
    logoTitle: string;
    logoHint: string;
    logoChoose: string;
    logoUpload: string;
    logoRemove: string;
  };
}
