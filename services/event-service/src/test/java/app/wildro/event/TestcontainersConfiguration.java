package app.wildro.event;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	/**
	 * Pinned, and pinned to the same tag the deployed database runs. Stock
	 * {@code postgres} has no PostGIS, so the first migration would fail here while
	 * passing on a developer machine that happened to have the extension. The
	 * substitute declaration is required: Testcontainers refuses an image whose name
	 * is not the one {@code PostgreSQLContainer} expects.
	 */
	public static final DockerImageName POSTGIS_IMAGE = DockerImageName
			.parse("postgis/postgis:17-3.5")
			.asCompatibleSubstituteFor("postgres");

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(POSTGIS_IMAGE);
	}

}
