package com.novel.agentic.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PreDestroy;

/**
 * Neo4j 图数据库配置
 */
@Configuration
@ConditionalOnProperty(name = "graph.neo4j.enabled", havingValue = "true", matchIfMissing = false)
public class Neo4jConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(Neo4jConfiguration.class);
    
    @Value("${graph.neo4j.uri:bolt://localhost:7687}")
    private String uri;
    
    @Value("${graph.neo4j.username:neo4j}")
    private String username;
    
    @Value("${graph.neo4j.password:novel_graph_2025}")
    private String password;
    
    private Driver driver;
    
    @Bean
    public Driver neo4jDriver() {
        logger.info("🔌 正在连接Neo4j图数据库: {}", uri);
        
        try {
            driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password));
            
            // 验证连接
            try (Session session = driver.session()) {
                session.run("RETURN 1").consume();
            }
            
            logger.info("✅ Neo4j图数据库连接成功！图谱数据将持久化保存");
            logger.info("   访问图谱浏览器: http://localhost:7474");
            
        } catch (Exception e) {
            logger.error("❌ Neo4j连接失败: {}", e.getMessage());
            logger.error("   请检查：1) Neo4j服务是否启动 2) 端口7687是否开放 3) 用户名密码是否正确");
            logger.error("   将降级使用内存存储（重启后数据丢失）");
            throw new RuntimeException("Neo4j连接失败", e);
        }
        
        return driver;
    }
    
    @PreDestroy
    public void cleanup() {
        if (driver != null) {
            logger.info("🔌 关闭Neo4j连接");
            driver.close();
        }
    }
}


