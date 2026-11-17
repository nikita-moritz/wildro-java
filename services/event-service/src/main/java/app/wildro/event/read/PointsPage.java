package app.wildro.event.read;

import java.util.List;

/**
 * The markers inside one map viewport. {@code truncated} says the area held
 * more than were sent; the map then asks the visitor to zoom in rather than
 * pretending it showed everything.
 */
public record PointsPage(List<EventPoint> items, boolean truncated) {
}
