package de.budgetbuddy.backend;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.TimeZone;

@Slf4j
@RestController
@SpringBootApplication
public class BackendApplication {
	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
		TimeZone.setDefault(TimeZone.getTimeZone("UTC+1"));
		log.info("Timezone was set to 'UTC+1'");
	}

	@GetMapping("/auth_test")
	ResponseEntity<ApiResponse<String>> test_authorization_interceptor() {
		return ResponseEntity
				.status(200)
				.body(new ApiResponse<>("OK"));
	}
}
