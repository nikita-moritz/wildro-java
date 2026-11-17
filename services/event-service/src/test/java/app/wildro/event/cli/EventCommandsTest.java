package app.wildro.event.cli;

import app.wildro.event.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The command as its caller sees it: one JSON object on stdout and an exit
 * code. The caller is often a language model, so "parses as JSON" is part of
 * the contract, not a nicety.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class EventCommandsTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final String DOCUMENT = """
			{ "events": [ {
			  "slug": "rursee-runde",
			  "status": "PUBLISHED",
			  "activityType": "HIKING",
			  "startsAt": "2031-05-10T10:00:00+02:00",
			  "startPoint": { "lat": 50.6344, "lon": 6.4127 },
			  "organizer": "Wandergruppe Eifel",
			  "source": { "url": "https://example.org/rursee", "readAt": "2031-05-01T12:00:00+02:00" },
			  "translations": {
			    "de": { "name": "Rursee-Runde", "placeName": "Heimbach, Deutschland", "description": "Rund um den See." },
			    "en": { "name": "Rursee loop", "placeName": "Heimbach, Germany", "description": "Around the lake." },
			    "ru": { "name": "Вокруг Рурзее", "placeName": "Хаймбах, Германия", "description": "Вокруг озера." }
			  }
			} ] }
			""";

	@Autowired
	private EventCommands commands;

	private Result run(String stdin, String... args) {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		InputStream in = new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8));
		int exit = this.commands.execute(CliRequest.parse(List.of(args)), in, new PrintStream(buffer, true,
				StandardCharsets.UTF_8));
		return new Result(exit, JSON.readTree(buffer.toString(StandardCharsets.UTF_8)));
	}

	@Test
	void theFirstRunIsADryRunAndSaysSo() {
		Result result = run(DOCUMENT, "import", "-", "--operator", "nikita");

		assertThat(result.exit()).isEqualTo(EventCommands.OK);
		assertThat(result.json().get("mode").asString()).isEqualTo("DRY_RUN");
		assertThat(result.json().at("/summary/create").asInt()).isEqualTo(1);
		assertThat(result.json().get("note").asString()).contains("--write");

		assertThat(run("", "list", "--all").json().get("events")).isEmpty();
	}

	@Test
	void withWriteItWritesAndTheListShowsIt() {
		assertThat(run(DOCUMENT, "import", "-", "--operator", "nikita", "--write").exit()).isEqualTo(EventCommands.OK);

		JsonNode list = run("", "list").json();
		assertThat(list.at("/events/0/slug").asString()).isEqualTo("rursee-runde");
		assertThat(list.at("/events/0/nameDe").asString()).isEqualTo("Rursee-Runde");
	}

	@Test
	void readsAFileAsWellAsStdin() throws Exception {
		Path file = Files.createTempFile("events", ".json");
		Files.writeString(file, DOCUMENT);

		Result result = run("", "import", file.toString(), "--operator", "nikita");

		assertThat(result.exit()).isEqualTo(EventCommands.OK);
		Files.delete(file);
	}

	@Test
	void aRefusalExitsWithOneAndListsWhereAndWhy() {
		Result result = run(DOCUMENT.replace("\"HIKING\"", "\"PARAGLIDING\""), "import", "-", "--operator", "nikita");

		assertThat(result.exit()).isEqualTo(EventCommands.REFUSED);
		assertThat(result.json().get("ok").asBoolean()).isFalse();
		assertThat(result.json().at("/violations/0/pointer").asString()).isEqualTo("/events/0/activityType");
		assertThat(result.json().at("/violations/0/reason").asString()).isNotBlank();
	}

	@Test
	void brokenJsonIsARefusalNotACrash() {
		Result result = run("{ \"events\": [ ", "import", "-", "--operator", "nikita");

		assertThat(result.exit()).isEqualTo(EventCommands.REFUSED);
		assertThat(result.json().at("/violations/0/pointer").asString()).isEqualTo("/");
	}

	@Test
	void aWrongConfirmationIsARefusalWithACode() {
		run(DOCUMENT, "import", "-", "--operator", "nikita", "--write");

		Result result = run("", "cancel", "rursee-runde", "--operator", "nikita", "--confirm", "rursee");

		assertThat(result.exit()).isEqualTo(EventCommands.REFUSED);
		assertThat(result.json().at("/error/code").asString()).isEqualTo("CONFIRMATION_MISMATCH");
	}

	@Test
	void aMissingFileIsAUsageError() {
		Result result = run("", "import", "/nonexistent/events.json", "--operator", "nikita");

		assertThat(result.exit()).isEqualTo(EventCommands.USAGE);
		assertThat(result.json().at("/error/code").asString()).isEqualTo("USAGE");
	}

	private record Result(int exit, JsonNode json) {
	}

}
