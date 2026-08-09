package com.clicker.mousehub.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class RealtimeExecutorConfig {
    @Bean(name = "realtimeFanoutExecutor")
    public ThreadPoolTaskExecutor realtimeFanoutExecutor(
            @Value("${app.realtime.fanout-threads:8}") int fanoutThreads,
            @Value("${app.realtime.fanout-queue-capacity:512}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int threads = Math.max(2, fanoutThreads);
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(Math.max(32, queueCapacity));
        executor.setThreadNamePrefix("realtime-fanout-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
