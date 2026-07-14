package com.knowledgehub.api.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class ChatAsyncConfigurationTest {

	@Test
	void startsAllChatWorkersImmediatelyAndRejectsExcessWork() throws InterruptedException {
		ThreadPoolTaskExecutor executor =
				(ThreadPoolTaskExecutor) new ChatAsyncConfiguration().chatExecutor();
		CountDownLatch workersStarted = new CountDownLatch(8);
		CountDownLatch releaseWorkers = new CountDownLatch(1);
		Runnable blockingChat = () -> {
			workersStarted.countDown();
			try {
				releaseWorkers.await();
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			}
		};

		try {
			for (int worker = 0; worker < 8; worker++) executor.execute(blockingChat);

			assertThat(workersStarted.await(2, TimeUnit.SECONDS)).isTrue();
			assertThatThrownBy(() -> executor.execute(blockingChat))
					.isInstanceOf(TaskRejectedException.class);
		} finally {
			releaseWorkers.countDown();
			executor.shutdown();
		}
	}
}
