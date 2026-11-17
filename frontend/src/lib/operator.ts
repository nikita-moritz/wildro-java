/**
 * Who runs the site, for the imprint and the privacy policy. Written once here
 * and never invented: the values are the ones the working version's imprint
 * states, and their source is ~/projects/entities (persons/nikita-moritz,
 * companies/nikone). A change there is a change here.
 */
export const OPERATOR = {
  name: "Nikita Moritz",
  street: "Stiftstr. 39",
  city: "52062 Aachen",
  country: "Deutschland",
  email: "nikita@nikone.dev",
  telegram: { handle: "@wildro_app", url: "https://t.me/wildro_app" },
  vatId: "DE462532622",
} as const;

/** The date the legal texts were last brought in line with what the app does. */
export const LEGAL_TEXTS_AS_OF = "03.11.2026";
