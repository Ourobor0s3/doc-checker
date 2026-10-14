package com.example.docchecker.pd.extract;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Parquet читается встроенным движком DuckDB (JDBC): схема столбцов и все значения построчно. */
@Component
public class ParquetFormatExtractor implements FormatExtractor {

    @Override
    public Set<FileFormat> formats() {
        return Set.of(FileFormat.PARQUET);
    }

    @Override
    public void extract(byte[] data, String fileName, ExtractionResult out) throws Exception {
        Path tmp = Files.createTempFile("pd-parquet-", ".parquet");
        try {
            Files.write(tmp, data);
            String path = tmp.toAbsolutePath().toString().replace("'", "''");
            try (Connection c = DriverManager.getConnection("jdbc:duckdb:");
                 Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM read_parquet('" + path + "')")) {
                ResultSetMetaData md = rs.getMetaData();
                int cols = md.getColumnCount();
                String[] names = new String[cols + 1];
                for (int i = 1; i <= cols; i++) {
                    names[i] = md.getColumnName(i);
                    out.header("столбец «" + names[i] + "»", names[i]);
                }
                long row = 0;
                while (rs.next()) {
                    row++;
                    for (int i = 1; i <= cols; i++) {
                        Object v = rs.getObject(i);
                        if (v != null && !out.field("строка " + row + ", столбец «" + names[i] + "»", String.valueOf(v), names[i])) {
                            return;
                        }
                    }
                }
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
