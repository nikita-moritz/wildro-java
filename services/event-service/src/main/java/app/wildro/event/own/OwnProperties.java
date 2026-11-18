package app.wildro.event.own;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The limits of SPEC.md §18.2, in configuration so they change without touching
 * the rules.
 *
 * @param publishedPerDay events one account may publish within any 24 hours
 * @param upcomingAtOnce drafts and published events still ahead, per account
 */
@ConfigurationProperties("wildro.own")
public record OwnProperties(int publishedPerDay, int upcomingAtOnce) {
}
