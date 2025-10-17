package com.novel;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 小说创作系统主应用类
 * 
 * @author Novel Creation System
 * @version 1.0.0
 * @since 2024-01-01
 */
@SpringBootApplication
@MapperScan({"com.novel.repository", "com.novel.mapper"})
@EnableCaching
@EnableAsync
@EnableScheduling
public class NovelCreationApplication {

    public static void main(String[] args) {
        SpringApplication.run(NovelCreationApplication.class, args);
        System.out.println("🚀 小说创作系统启动成功");
        System.out.println("📚 访问地址: http://localhost:8080/api");
        System.out.println("🔍 健康检测 http://localhost:8080/api/actuator/health");
    }
} 
