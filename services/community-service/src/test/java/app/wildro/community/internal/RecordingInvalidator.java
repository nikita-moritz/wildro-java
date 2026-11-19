package app.wildro.community.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.web.client.RestClient;

/** Keeps the tags it was asked to drop instead of calling the frontend. */
public class RecordingInvalidator extends CacheInvalidator {

	private final List<String> tags = new ArrayList<>();

	public RecordingInvalidator() {
		super(RestClient.builder(), "", "");
	}

	@Override
	public synchronized void invalidate(Collection<String> tags) {
		this.tags.addAll(tags);
	}

	public synchronized List<String> tags() {
		return List.copyOf(this.tags);
	}

	public synchronized void reset() {
		this.tags.clear();
	}

}
