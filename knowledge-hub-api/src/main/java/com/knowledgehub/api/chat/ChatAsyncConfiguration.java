package com.knowledgehub.api.chat;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
class ChatAsyncConfiguration {

	@Bean(value = "chatExecutor", destroyMethod = "shutdown")
	Executor chatExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(8);
		executor.setMaxPoolSize(8);
		executor.setQueueCapacity(0);
		executor.setThreadNamePrefix("chat-stream-");
		executor.initialize();
		return executor;
	}

	@Bean(value = "chatHeartbeatScheduler", destroyMethod = "shutdown")
	ScheduledExecutorService chatHeartbeatScheduler() {
		AtomicInteger threadNumber = new AtomicInteger();
		return Executors.newScheduledThreadPool(8, runnable -> {
			Thread thread = new Thread(runnable, "chat-heartbeat-" + threadNumber.incrementAndGet());
			thread.setDaemon(true);
			return thread;
		});
	}
}
