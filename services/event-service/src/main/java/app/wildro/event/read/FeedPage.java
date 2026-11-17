package app.wildro.event.read;

import java.util.List;

/**
 * One page of the feed. {@code hasNext} and no total: the total would cost a
 * count query on every request, crawlers walk these pages constantly, and
 * nobody reads the number.
 */
public record FeedPage(List<EventCard> items, int page, int size, boolean hasNext) {
}
