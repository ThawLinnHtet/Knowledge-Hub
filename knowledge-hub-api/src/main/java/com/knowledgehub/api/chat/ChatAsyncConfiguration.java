package com.knowledgehub.api.chat;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
class ChatAsyncConfiguration {

	@Bean(value = "chatExecutor", destroyMethod = "shutdown")
	Executor chatExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(8);
		executor.setQueueCapacity(100);
		executor.setThreadNamePrefix("chat-stream-");
		executor.initialize();
		return executor;
	}
}
