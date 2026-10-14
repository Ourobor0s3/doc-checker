package com.example.docchecker.pd;

import com.example.docchecker.config.AppProperties;
import com.example.docchecker.pd.detect.RuleEngine;
import com.example.docchecker.pd.extract.FormatDetector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class PdConfig {

    @Bean
    public FormatDetector formatDetector() {
        return new FormatDetector();
    }

    @Bean
    public RuleEngine ruleEngine() {
        return new RuleEngine();
    }

    /** Очередь сканирований. По умолчанию один воркер: Ollama плохо параллелит запросы. */
    @Bean("scanExecutor")
    public ThreadPoolTaskExecutor scanExecutor(AppProperties props) {
        int workers = Math.max(1, props.pd().workers());
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(workers);
        ex.setMaxPoolSize(workers);
        ex.setQueueCapacity(props.pd().queueCapacity());
        ex.setThreadNamePrefix("scan-");
        return ex;
    }
}
