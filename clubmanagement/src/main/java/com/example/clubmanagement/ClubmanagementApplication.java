package com.example.clubmanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ClubmanagementApplication {

	public static void main(String[] args) {
		SpringApplication.run(ClubmanagementApplication.class, args);
	}

}

