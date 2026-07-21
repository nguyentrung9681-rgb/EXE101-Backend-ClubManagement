package com.example.clubmanagement.Config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class DatabaseConfig {

    @Bean
    public CommandLineRunner updateDatabaseConstraints(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                // Drop the outdated check constraint
                jdbcTemplate.execute("ALTER TABLE club_member DROP CONSTRAINT IF EXISTS club_member_role_check");
                // Re-add it with the new enum value included
                jdbcTemplate.execute("ALTER TABLE club_member ADD CONSTRAINT club_member_role_check CHECK (role IN ('PRESIDENT', 'DEPARTMENT_HEAD', 'TREASURER', 'MEMBER'))");
                System.out.println("=== Cập nhật database check constraint club_member_role_check thành công! ===");

                // Drop the outdated document type check constraint
                jdbcTemplate.execute("ALTER TABLE club_document DROP CONSTRAINT IF EXISTS club_document_document_type_check");
                // Re-add it with the current document types
                jdbcTemplate.execute("ALTER TABLE club_document ADD CONSTRAINT club_document_document_type_check CHECK (document_type IN ('MEETING_MINUTES', 'EVENT_PLAN', 'REPORT', 'FINANCE', 'OTHER'))");
                System.out.println("=== Cập nhật database check constraint club_document_document_type_check thành công! ===");
            } catch (Exception e) {
                System.err.println("=== Lỗi khi cập nhật database check constraint: " + e.getMessage() + " ===");
            }
        };
    }
}
