package com.aigreentick.services.template.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for async message dispatching.
 * 
 * Thread pool is sized based on:
 * - Core pool: handles normal load
 * - Max pool: handles peak load bursts
 * - Queue capacity: buffers requests during spikes
 */
@Configuration
@EnableAsync
public class AsyncBatchDispatchConfig {

    @Bean(name = "messageDispatchExecutor")
    public Executor messageDispatchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // Core threads for steady-state processing
        executor.setCorePoolSize(10);
        
        // Max threads for burst capacity
        executor.setMaxPoolSize(20);
        
        // Queue capacity - holds tasks when all threads busy
        executor.setQueueCapacity(100);
        
        // Thread naming for debugging
        executor.setThreadNamePrefix("dispatch-");
        
        // Wait for tasks to complete on shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        
        executor.initialize();
        return executor;
    }
}