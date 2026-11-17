package app.wildro.event.read;

/**
 * The rectangle the map currently shows, in WGS 84 degrees. A viewport that
 * crosses the antimeridian is not supported: nothing wildro lists is near it.
 */
public record Viewport(double south, double west, double north, double east) {

	public Viewport {
		if (south < -90 || north > 90 || west < -180 || east > 180) {
			throw new IllegalArgumentException("viewport out of range");
		}
		if (north <= south || east <= west) {
			throw new IllegalArgumentException("viewport must have north above south and east right of west");
		}
	}

}
