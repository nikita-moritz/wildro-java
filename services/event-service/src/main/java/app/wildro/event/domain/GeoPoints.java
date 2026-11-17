package app.wildro.event.domain;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

/**
 * The only place a point is made from coordinates.
 * <p>
 * JTS takes x then y, and x is longitude. Every caller writing
 * {@code new Coordinate(lat, lon)} is one transposition away from putting Aachen
 * in the Indian Ocean, so the order is fixed here once, behind named parameters.
 */
public final class GeoPoints {

	/** WGS 84, what GPS and every map on the web speak. */
	public static final int WGS84 = 4326;

	private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), WGS84);

	private GeoPoints() {
	}

	public static Point of(double latitude, double longitude) {
		return FACTORY.createPoint(new Coordinate(longitude, latitude));
	}

}
