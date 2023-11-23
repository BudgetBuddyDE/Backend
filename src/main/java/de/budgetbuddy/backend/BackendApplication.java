package de.budgetbuddy.backend;

import de.budgetbuddy.backend.log.LogRepository;
import de.budgetbuddy.backend.log.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SpringBootApplication
public class BackendApplication {
	@Autowired
	public BackendApplication(LogRepository logRepository) {
		Logger.getInstance().setLogRepository(logRepository);
	}

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

	@GetMapping("/auth_test")
	ResponseEntity<ApiResponse<String>> test_authorization_interceptor() {
		return ResponseEntity
				.status(200)
				.body(new ApiResponse<>("OK"));
	}
}
