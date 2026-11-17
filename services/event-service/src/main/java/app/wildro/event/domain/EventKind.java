package app.wildro.event.domain;

/**
 * Where the event lives. Stage one knows only {@link #EXTERNAL}.
 * <p>
 * The distinction is not cosmetic: an external event is announced in someone
 * else's group, signing up happens there and not here, so it must carry a link
 * out and must never show a capacity taken from that source. An own event is
 * ours, and arrives with booking in stage two.
 */
public enum EventKind {

	EXTERNAL,
	OWN

}
