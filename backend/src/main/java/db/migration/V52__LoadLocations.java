package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.zip.GZIPInputStream;

/**
 * Loads the countries, states and cities of V51 from {@code db/locations/*.tsv.gz} (made by
 * scripts/generate-locations.py): tab-separated, one row per line, empty fields are null.
 */
public class V52__LoadLocations extends BaseJavaMigration {

	private static final int BATCH = 2000;

	@Override
	public void migrate(Context context) throws Exception {
		Connection connection = context.getConnection();
		load(connection, "countries.tsv.gz", "INSERT INTO countries (code, name, iso3, phone_code) VALUES (?, ?, ?, ?)",
				new int[] { Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR });
		load(connection, "states.tsv.gz", "INSERT INTO states (id, country_code, name, code) VALUES (?, ?, ?, ?)",
				new int[] { Types.INTEGER, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR });
		load(connection, "cities.tsv.gz", "INSERT INTO cities (id, state_id, name) VALUES (?, ?, ?)",
				new int[] { Types.INTEGER, Types.INTEGER, Types.VARCHAR });
	}

	private static void load(Connection connection, String file, String sql, int[] types) throws IOException, SQLException {
		try (InputStream raw = V52__LoadLocations.class.getResourceAsStream("/db/locations/" + file)) {
			if (raw == null) {
				throw new IllegalStateException("Missing db/locations/" + file);
			}
			try (BufferedReader in = new BufferedReader(new InputStreamReader(new GZIPInputStream(raw), StandardCharsets.UTF_8));
					PreparedStatement insert = connection.prepareStatement(sql)) {
				int pending = 0;
				for (String line = in.readLine(); line != null; line = in.readLine()) {
					if (line.isEmpty()) {
						continue;
					}
					String[] fields = line.split("\t", -1);
					for (int i = 0; i < types.length; i++) {
						String value = i < fields.length ? fields[i] : "";
						if (value.isEmpty()) {
							insert.setNull(i + 1, types[i]);
						}
						else if (types[i] == Types.INTEGER) {
							insert.setInt(i + 1, Integer.parseInt(value));
						}
						else {
							insert.setString(i + 1, value);
						}
					}
					insert.addBatch();
					if (++pending == BATCH) {
						insert.executeBatch();
						pending = 0;
					}
				}
				if (pending > 0) {
					insert.executeBatch();
				}
			}
		}
	}

}
