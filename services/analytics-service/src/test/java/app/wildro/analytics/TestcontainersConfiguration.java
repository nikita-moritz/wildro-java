package app.wildro.analytics;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	/**
	 * The same image the deployed database runs in: wildro_analytics lives in the
	 * wildro-postgres container. PostGIS is not used here, the tag is pinned for
	 * parity with production, not for the extension.
	 */
	public static final DockerImageName IMAGE = DockerImageName.parse("postgis/postgis:17-3.5")
		.asCompatibleSubstituteFor("postgres");

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(IMAGE);
	}

	/** A clock the tests move by hand: the daily salt and the rate window depend on it. */
	@Bean
	@Primary
	MutableClock testClock() {
		return new MutableClock();
	}

}
