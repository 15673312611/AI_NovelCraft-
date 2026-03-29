package com.novel.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.Executor;

/**
 * 异步配置
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Bean(name = {"novelTaskExecutor", "taskExecutor"})
    public Executor novelTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("NovelAI-");
        executor.setKeepAliveSeconds(60);
        executor.setTaskDecorator(new SecurityContextCopyingTaskDecorator());
        executor.initialize();
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return novelTaskExecutor();
    }

    private static final class SecurityContextCopyingTaskDecorator implements TaskDecorator {
        @Override
        public Runnable decorate(Runnable runnable) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            return () -> {
                SecurityContext asyncContext = SecurityContextHolder.createEmptyContext();
                if (authentication != null) {
                    asyncContext.setAuthentication(authentication);
                }
                SecurityContextHolder.setContext(asyncContext);
                try {
                    runnable.run();
                } finally {
                    SecurityContextHolder.clearContext();
                }
            };
        }
    }
}
