package app.wildro.event.admin;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.path.NodePath;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * The published import schema, used as the input check.
 * <p>
 * SPEC.md §0.1 asks for one file that is both the model's instructions and the
 * check at the door. Validating against the very file the documentation points
 * to is what makes that true: there is no second description that could drift.
 */
@Component
public class ImportSchema {

	/** Where the schema lives on the classpath, and in the repository under src/main/resources. */
	public static final String LOCATION = "import/event-import.schema.json";

	/** Keywords whose error sits on the parent object and names the child in {@link Error#getProperty()}. */
	private static final Set<String> CHILD_KEYWORDS = Set.of("required", "additionalProperties");

	private final Schema schema;

	ImportSchema() {
		// Draft 2020-12 treats "format" as an annotation unless told otherwise, which
		// would let "startsAt": "next Saturday" through. It is an assertion here.
		SchemaRegistryConfig config = SchemaRegistryConfig.builder().formatAssertionsEnabled(true).build();
		SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
				builder -> builder.schemaRegistryConfig(config));
		try (InputStream in = ImportSchema.class.getClassLoader().getResourceAsStream(LOCATION)) {
			if (in == null) {
				throw new IllegalStateException(LOCATION + " is missing from the classpath");
			}
			this.schema = registry.getSchema(in);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("cannot read " + LOCATION, ex);
		}
	}

	/** Every way the document breaks the schema, in document order. Empty means it conforms. */
	public List<Violation> validate(JsonNode document) {
		return this.schema.validate(document).stream()
				.map(ImportSchema::toViolation)
				.distinct()
				.sorted(Comparator.comparing(Violation::pointer))
				.toList();
	}

	private static Violation toViolation(Error error) {
		String pointer = pointerOf(error.getInstanceLocation());
		if (CHILD_KEYWORDS.contains(error.getKeyword()) && error.getProperty() != null) {
			pointer = pointer + "/" + escape(error.getProperty());
		}
		return new Violation(pointer.isEmpty() ? "/" : pointer, error.getMessage());
	}

	/** Built from the path's elements rather than its string form, which depends on configuration. */
	private static String pointerOf(NodePath path) {
		StringBuilder pointer = new StringBuilder();
		for (int i = 0; i < path.getNameCount(); i++) {
			pointer.append('/').append(escape(String.valueOf(path.getElement(i))));
		}
		return pointer.toString();
	}

	/** RFC 6901: "~" and "/" inside a key are written as "~0" and "~1". */
	private static String escape(String token) {
		return token.replace("~", "~0").replace("/", "~1");
	}

}
