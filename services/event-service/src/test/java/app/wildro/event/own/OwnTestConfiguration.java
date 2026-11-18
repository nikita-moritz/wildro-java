package app.wildro.event.own;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class OwnTestConfiguration {

	@Bean
	@Primary
	FakeWriters fakeWriters() {
		return new FakeWriters();
	}

	@Bean
	@Primary
	FakeCommunities fakeCommunities() {
		return new FakeCommunities();
	}

}
