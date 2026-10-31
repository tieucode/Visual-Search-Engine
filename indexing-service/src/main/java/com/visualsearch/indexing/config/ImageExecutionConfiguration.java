package com.visualsearch.indexing.config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
@Configuration
public class ImageExecutionConfiguration {
    @Bean
    ThreadPoolTaskExecutor imagePreparationExecutor(IndexingProperties properties) {
        var executor = new ThreadPoolTaskExecutor();
        int concurrency = properties.getImage().getPreparationConcurrency();
        executor.setCorePoolSize(concurrency);
        executor.setMaxPoolSize(concurrency);
        executor.setQueueCapacity(properties.getAi().getBatchSize());
        executor.setThreadNamePrefix("image-preparation-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setAwaitTerminationSeconds((int) properties.getRabbit().getShutdownTimeout().toSeconds());
        return executor;
    }
}
