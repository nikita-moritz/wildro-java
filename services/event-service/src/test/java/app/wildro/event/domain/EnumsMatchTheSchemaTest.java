package app.wildro.event.domain;

import app.wildro.event.admin.ImportSchema;
import app.wildro.event.audit.AuditAction;
import app.wildro.event.audit.AuditChannel;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every enumeration is written down more than once, which is a copy. It is a
 * deliberate one, each place doing a job the others cannot: the Java enum
 * refuses a typo at compile time, the check constraint refuses an unknown value
 * even when the writer is not this application, and the import schema tells a
 * language model what it may write and refuses it at the door.
 * <p>
 * What must not happen is the copies drifting apart, so the seams are held by
 * this test rather than by remembering. Adding a value means the enum, a
 * migration and the schema; this test failing is that reminder.
 */
class EnumsMatchTheSchemaTest {

	private static final String EVENTS = "db/migration/V2__event.sql";

	private static final String LINKS = "db/migration/V4__event_links.sql";

	/** Redefines the status and both audit constraints; the latest definition is the one that holds. */
	private static final String STAGE_TWO = "db/migration/V9__own_events_and_reports.sql";

	private static final String COVERS = "db/migration/V14__uploaded_cover.sql";

	private static final JsonNode IMPORT_SCHEMA = JsonMapper.builder().build().readTree(resource(ImportSchema.LOCATION));

	@Test
	void activityTypesMatch() {
		Set<String> java = namesOf(ActivityType.values());
		assertThat(constraintValues(EVENTS, "events_activity_type_known")).isEqualTo(java);
		assertThat(importEnum("activityType")).isEqualTo(java);
	}

	@Test
	void difficultiesMatch() {
		Set<String> java = namesOf(Difficulty.values());
		assertThat(constraintValues(EVENTS, "events_difficulty_known")).isEqualTo(java);
		assertThat(importEnum("difficulty")).isEqualTo(java);
	}

	@Test
	void kindsMatch() {
		assertThat(constraintValues(EVENTS, "events_kind_known")).isEqualTo(namesOf(EventKind.values()));
	}

	/**
	 * The import schema offers every status but two on purpose: cancelling is a
	 * separate command (SPEC.md §0.1) and suspension a moderation decision
	 * (§18.2), never a value in a document.
	 */
	@Test
	void statusesMatch() {
		assertThat(constraintValues(STAGE_TWO, "events_status_known")).isEqualTo(namesOf(EventStatus.values()));
		assertThat(importEnum("status"))
				.isEqualTo(namesOf(EnumSet.complementOf(EnumSet.of(EventStatus.CANCELLED, EventStatus.SUSPENDED)).toArray(new EventStatus[0])));
	}

	/** The document names the scopes in lower case, as keys of its links object. */
	@Test
	void linkScopesMatch() {
		assertThat(constraintValues(LINKS, "event_links_scope_known")).isEqualTo(namesOf(LinkScope.values()));
		assertThat(IMPORT_SCHEMA.at("/$defs/event/properties/links/properties").propertyNames())
				.containsExactlyInAnyOrderElementsOf(
						Arrays.stream(LinkScope.values()).map(LinkScope::documentKey).toList());
	}

	@Test
	void theLocalesAreTheSameEverywhere() {
		assertThat(constraintValues(EVENTS, "event_translations_locale_known")).isEqualTo(Event.LOCALES);

		JsonNode translations = IMPORT_SCHEMA.at("/$defs/event/properties/translations");
		assertThat(strings(translations.get("required"))).isEqualTo(Event.LOCALES);
		assertThat(translations.get("properties").propertyNames()).containsExactlyInAnyOrderElementsOf(Event.LOCALES);
	}

	@Test
	void auditActionsMatch() {
		assertThat(constraintValues(COVERS, "audit_log_action_known")).isEqualTo(namesOf(AuditAction.values()));
	}

	@Test
	void auditChannelsMatch() {
		assertThat(constraintValues(STAGE_TWO, "audit_log_channel_known")).isEqualTo(namesOf(AuditChannel.values()));
	}

	private static Set<String> importEnum(String property) {
		JsonNode values = IMPORT_SCHEMA.at("/$defs/event/properties/" + property + "/enum");
		assertThat(values.isArray()).as("%s has an enum in the import schema", property).isTrue();
		return strings(values);
	}

	private static Set<String> strings(JsonNode array) {
		return StreamSupport.stream(array.spliterator(), false).map(JsonNode::asString).collect(Collectors.toSet());
	}

	private static Set<String> namesOf(Enum<?>[] values) {
		return Arrays.stream(values).map(Enum::name).collect(Collectors.toSet());
	}

	/** Pulls the quoted literals out of {@code CONSTRAINT <name> CHECK (... IN ('A', 'B'))}. */
	private static Set<String> constraintValues(String migration, String constraintName) {
		Matcher constraint = Pattern
				.compile("CONSTRAINT\\s+" + constraintName + "\\s+CHECK\\s*\\((.*?)\\)\\s*\\)", Pattern.DOTALL)
				.matcher(resource(migration));
		assertThat(constraint.find()).as("constraint %s is present in %s", constraintName, migration).isTrue();

		Set<String> values = Pattern.compile("'([^']+)'").matcher(constraint.group(1)).results()
				.map(result -> result.group(1))
				.collect(Collectors.toSet());
		assertThat(values).as("constraint %s lists no values", constraintName).isNotEmpty();
		return values;
	}

	private static String resource(String location) {
		try (InputStream in = EnumsMatchTheSchemaTest.class.getClassLoader().getResourceAsStream(location)) {
			assertThat(in).as("%s is on the classpath", location).isNotNull();
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalStateException("cannot read " + location, ex);
		}
	}

}
