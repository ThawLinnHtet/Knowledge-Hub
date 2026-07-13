package com.knowledgehub.api.common;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knowledgehub.api.TestcontainersConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, ErrorContractIntegrationTest.ValidationController.class})
@AutoConfigureMockMvc
@SpringBootTest
class ErrorContractIntegrationTest {

	private final MockMvc mockMvc;

	@Autowired
	ErrorContractIntegrationTest(MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	@Test
	@WithMockUser
	void validationFailureUsesStandardErrorContract() throws Exception {
		mockMvc.perform(post("/api/v1/test/validation")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(header().exists("X-Request-ID"))
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.message").value("One or more fields are invalid."))
				.andExpect(jsonPath("$.requestId").isNotEmpty())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
				.andExpect(jsonPath("$.fieldErrors[0].code").value("NotBlank"))
				.andExpect(jsonPath("$.metadata").isMap());
	}

	@Test
	void corsPreflightAllowsCookieCsrfHeader() throws Exception {
		mockMvc.perform(options("/api/v1/auth/refresh")
					.header("Origin", "http://localhost:5173")
					.header("Access-Control-Request-Method", "POST")
					.header("Access-Control-Request-Headers", "X-XSRF-TOKEN"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
				.andExpect(header().string("Access-Control-Allow-Headers", "X-XSRF-TOKEN"));
	}

	@RestController
	static class ValidationController {

		@PostMapping("/api/v1/test/validation")
		void validate(@Valid @RequestBody ValidationRequest request) {}
	}

	record ValidationRequest(@NotBlank String name) {}
}
