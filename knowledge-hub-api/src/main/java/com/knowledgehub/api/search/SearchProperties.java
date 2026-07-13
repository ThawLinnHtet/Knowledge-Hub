package com.knowledgehub.api.search;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("app.search")
@Validated
public record SearchProperties(
		@Positive int maxResults,
		@Positive int maxQueryCharacters,
		@DecimalMin("0.0") @DecimalMax("1.0") double minimumSemanticScore) {}
