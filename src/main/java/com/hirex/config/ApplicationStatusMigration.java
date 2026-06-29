package com.hirex.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

@Component
public class ApplicationStatusMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ApplicationStatusMigration.class);
    private final DataSource dataSource;

    public ApplicationStatusMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            String dbName = conn.getCatalog();
            int currentLength = 0;

            String checkSql = String.format(
                    "SELECT CHARACTER_MAXIMUM_LENGTH " +
                            "FROM information_schema.COLUMNS " +
                            "WHERE TABLE_SCHEMA = '%s' AND TABLE_NAME = 'applications' AND COLUMN_NAME = 'status'",
                    dbName
            );

            try (ResultSet rs = stmt.executeQuery(checkSql)) {
                if (rs.next()) currentLength = rs.getInt(1);
            }

            if (currentLength < 30) {
                log.warn("Widening applications.status from VARCHAR({}) to VARCHAR(30)", currentLength);
                stmt.executeUpdate("ALTER TABLE applications MODIFY COLUMN status VARCHAR(30)");
                log.info("applications.status widened successfully.");
            }

        } catch (Exception e) {
            log.error("Could not widen applications.status column: {}", e.getMessage(), e);
        }
    }
}