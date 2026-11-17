package db.migration;

import app.wildro.event.domain.SearchText;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fills events.search_text for rows written before V7, through
 * {@link SearchText#fold}, the same rule the entity applies, so no SQL copy of
 * it exists. Organiser first, then name and place per language in language
 * order, as {@code SearchText.of} joins them. Then the column becomes NOT NULL.
 */
public class V8__fill_search_text extends BaseJavaMigration {

	@Override
	public void migrate(Context context) throws Exception {
		Connection db = context.getConnection();
		Map<Long, List<String>> parts = new LinkedHashMap<>();
		try (Statement select = db.createStatement();
				ResultSet rows = select.executeQuery("""
						select e.id, e.organizer, t.name, t.place_name
						from events e left join event_translations t on t.event_id = e.id
						order by e.id, t.locale
						""")) {
			while (rows.next()) {
				List<String> list = parts.computeIfAbsent(rows.getLong(1), id -> new ArrayList<>());
				if (list.isEmpty()) {
					list.add(rows.getString(2));
				}
				if (rows.getString(3) != null) {
					list.add(rows.getString(3));
					list.add(rows.getString(4));
				}
			}
		}
		try (PreparedStatement update = db.prepareStatement("update events set search_text = ? where id = ?")) {
			for (Map.Entry<Long, List<String>> row : parts.entrySet()) {
				update.setString(1, SearchText.fold(String.join(" ", row.getValue())));
				update.setLong(2, row.getKey());
				update.addBatch();
			}
			update.executeBatch();
		}
		try (Statement alter = db.createStatement()) {
			alter.execute("alter table events alter column search_text set not null");
		}
	}

}
