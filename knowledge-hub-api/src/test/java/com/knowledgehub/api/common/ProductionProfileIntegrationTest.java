package com.knowledgehub.api.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knowledgehub.api.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles({"test", "prod"})
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest
class ProductionProfileIntegrationTest {

	private final MockMvc mockMvc;

	@Autowired
	ProductionProfileIntegrationTest(MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	@Test
	void disablesOpenApiAndSwaggerUi() throws Exception {
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
		mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
	}
}
