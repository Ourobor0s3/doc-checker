package com.example.docchecker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ExecutorConfig {

    /** Очередь проверок. Ollama плохо параллелит запросы, поэтому по умолчанию один воркер. */
    @Bean("checkExecutor")
    public ThreadPoolTaskExecutor checkExecutor(AppProperties props) {
        int workers = Math.max(1, props.check().workers());
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(workers);
        ex.setMaxPoolSize(workers);
        ex.setQueueCapacity(props.check().queueCapacity());
        ex.setThreadNamePrefix("check-");
        return ex;
    }
}
