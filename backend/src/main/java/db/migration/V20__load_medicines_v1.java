package db.migration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.zip.CRC32;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Loads the demonstration medicine list from the CSV files in {@code resources/data}. It has
 * not been clinically reviewed. To change it, add a new file version and a new migration: the
 * checksum makes Flyway refuse to start if a file that was already loaded is edited.
 */
public class V20__load_medicines_v1 extends BaseJavaMigration {

    private static final String GROUPS = "data/allergy-groups-v1.csv";
    private static final String MEDICINES = "data/medicines-v1.csv";
    private static final String LIST_VERSION = "medicines-v1";
    private static final int MEDICINE_COLUMNS = 7;

    @Override
    public void migrate(Context context) throws SQLException {
        try (PreparedStatement insert =
                context.getConnection().prepareStatement("insert into allergy_group (code, label) values (?, ?)")) {
            for (String line : rows(GROUPS)) {
                String[] row = line.split(",", -1);
                insert.setString(1, row[0]);
                insert.setString(2, row[1]);
                insert.addBatch();
            }
            insert.executeBatch();
        }
        try (PreparedStatement insert = context.getConnection().prepareStatement("""
                insert into medicine (code, name, form, strength, group_code, routes, price_kes, list_version)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (String line : rows(MEDICINES)) {
                String[] row = line.split(",", -1);
                if (row.length != MEDICINE_COLUMNS) {
                    throw new IllegalStateException("Bad line in " + MEDICINES + ": " + line);
                }
                for (int column = 0; column < MEDICINE_COLUMNS - 1; column++) {
                    insert.setString(column + 1, row[column]);
                }
                insert.setBigDecimal(MEDICINE_COLUMNS, new BigDecimal(row[MEDICINE_COLUMNS - 1]));
                insert.setString(MEDICINE_COLUMNS + 1, LIST_VERSION);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    /** Computed from the lines, so Windows and Linux line endings give the same checksum. */
    @Override
    public Integer getChecksum() {
        CRC32 crc = new CRC32();
        for (String file : List.of(GROUPS, MEDICINES)) {
            for (String line : rows(file)) {
                crc.update(line.getBytes(StandardCharsets.UTF_8));
            }
        }
        return (int) crc.getValue();
    }

    /** The file's data lines, without the header. */
    private static List<String> rows(String file) {
        try (InputStream stream = V20__load_medicines_v1.class.getClassLoader().getResourceAsStream(file)) {
            if (stream == null) {
                throw new IllegalStateException("Missing " + file);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                return reader.lines().skip(1).filter(line -> !line.isBlank()).toList();
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
