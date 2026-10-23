package com.project.learning_platform;

import com.project.learning_platform.support.TestcontainersConfiguration;
import org.springframework.boot.SpringApplication;

/**
 * Runs the application locally against a throwaway PostgreSQL container
 * ({@code ./mvnw spring-boot:test-run}). Data is lost when it stops.
 */
public class TestLearningPlatformApplication {

	public static void main(String[] args) {
		SpringApplication.from(LearningPlatformApplication::main)
				.with(TestcontainersConfiguration.class)
				.withAdditionalProfiles("dev")
				.run(args);
	}

}
