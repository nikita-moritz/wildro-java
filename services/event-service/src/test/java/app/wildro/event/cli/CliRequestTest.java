package app.wildro.event.cli;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CliRequestTest {

	@Test
	void parsesAnImport() {
		CliRequest request = CliRequest.parse(List.of("import", "events.json", "--operator", "nikita", "--write"));

		assertThat(request.command()).isEqualTo("import");
		assertThat(request.target()).isEqualTo("events.json");
		assertThat(request.options()).containsEntry("operator", "nikita");
		assertThat(request.switches()).containsExactly("write");
	}

	@Test
	void withoutWriteAnImportIsADryRun() {
		CliRequest request = CliRequest.parse(List.of("import", "-", "--operator", "nikita"));

		assertThat(request.switches()).doesNotContain("write");
	}

	/**
	 * The dangerous typo: an ignored "--wirte" would silently turn a real run into
	 * a dry run. Refused for the same reason the schema refuses unknown keys.
	 */
	@Test
	void aMistypedFlagIsRefused() {
		assertThatThrownBy(() -> CliRequest.parse(List.of("import", "e.json", "--operator", "n", "--wirte")))
				.isInstanceOf(CliRequest.UsageException.class)
				.hasMessageContaining("--wirte");
	}

	@Test
	void everyWriteNamesItsOperator() {
		assertThatThrownBy(() -> CliRequest.parse(List.of("import", "e.json")))
				.hasMessageContaining("--operator");
		assertThatThrownBy(() -> CliRequest.parse(List.of("cancel", "slug", "--confirm", "slug")))
				.hasMessageContaining("--operator");
	}

	@Test
	void deletingNeedsConfirmationAndAReason() {
		assertThatThrownBy(() -> CliRequest.parse(List.of("delete", "slug", "--operator", "n", "--confirm", "slug")))
				.hasMessageContaining("--reason");
	}

	@Test
	void anOptionWithoutAValueIsRefused() {
		assertThatThrownBy(() -> CliRequest.parse(List.of("import", "e.json", "--operator", "--write")))
				.hasMessageContaining("needs a value");
	}

	@Test
	void anUnknownCommandIsRefused() {
		assertThatThrownBy(() -> CliRequest.parse(List.of("drop"))).hasMessageContaining("unknown command");
	}

	@Test
	void listTakesPaging() {
		CliRequest request = CliRequest.parse(List.of("list", "--all", "--size", "10"));

		assertThat(request.switches()).containsExactly("all");
		assertThat(request.intOption("size", 50)).isEqualTo(10);
		assertThat(request.intOption("page", 0)).isZero();
	}

}
