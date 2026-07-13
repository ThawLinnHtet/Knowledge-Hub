package com.knowledgehub.api.ingestion;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class IngestionConfiguration {

	@Bean
	DocumentChunker documentChunker(IngestionProperties properties) {
		return new DocumentChunker(properties.chunkSize(), properties.chunkOverlap());
	}
}
