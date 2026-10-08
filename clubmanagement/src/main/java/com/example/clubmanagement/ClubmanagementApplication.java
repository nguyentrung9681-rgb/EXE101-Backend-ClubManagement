package com.example.clubmanagement;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class ClubmanagementApplication {

	public static void main(String[] args) {
		SpringApplication.run(ClubmanagementApplication.class, args);
	}

	@Bean
	public CommandLineRunner cleanupOldPermissionColumns(JdbcTemplate jdbcTemplate) {
		return args -> {
			try {
				jdbcTemplate.execute("ALTER TABLE club_google_permission DROP COLUMN IF EXISTS can_create CASCADE;");
				jdbcTemplate.execute("ALTER TABLE club_google_permission DROP COLUMN IF EXISTS can_delete CASCADE;");
				jdbcTemplate.execute("ALTER TABLE club_google_permission DROP COLUMN IF EXISTS can_edit_title CASCADE;");
				jdbcTemplate.execute("ALTER TABLE club_google_permission DROP COLUMN IF EXISTS can_edit_data CASCADE;");
				jdbcTemplate.execute("ALTER TABLE club_google_permission DROP COLUMN IF EXISTS can_edit_type CASCADE;");
				System.out.println("=== Xóa bỏ hoàn toàn các cột phân quyền cũ (can_create...) khỏi database Supabase thành công! ===");
			} catch (Exception e) {
				System.err.println("Dọn dẹp cột phân quyền cũ: " + e.getMessage());
			}
		};
	}
}
