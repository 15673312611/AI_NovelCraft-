package com.novel.agentic.service.performance;

import com.novel.agentic.model.GraphEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图谱查询缓存
 * 
 * 解决问题：频繁的图谱查询可能导致性能问题
 * 
 * 策略：
 * 1. 缓存最近查询结果（5分钟有效期）
 * 2. 新章节入图后自动失效相关缓存
 * 3. 定时清理过期缓存
 */
@Component
public class GraphQueryCache {
    
    private static final Logger logger = LoggerFactory.getLogger(GraphQueryCache.class);
    
    // 缓存有效期（毫秒）
    private static final long CACHE_TTL = 5 * 60 * 1000; // 5分钟
    
    // 缓存存储
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    
    /**
     * 获取缓存
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key) {
        CacheEntry entry = cache.get(key);
        
        if (entry == null) {
            return Optional.empty();
        }
        
        // 检查是否过期
        if (System.currentTimeMillis() - entry.timestamp > CACHE_TTL) {
            cache.remove(key);
            logger.debug("缓存过期: {}", key);
            return Optional.empty();
        }
        
        logger.debug("缓存命中: {}", key);
        return Optional.of((T) entry.data);
    }
    
    /**
     * 设置缓存
     */
    public void put(String key, Object data) {
        CacheEntry entry = new CacheEntry();
        entry.timestamp = System.currentTimeMillis();
        entry.data = data;
        
        cache.put(key, entry);
        logger.debug("缓存设置: {}", key);
    }
    
    /**
     * 构建查询键
     */
    public String buildKey(String queryType, Long novelId, Integer chapterNumber, Object... params) {
        StringBuilder key = new StringBuilder();
        key.append(queryType).append(":").append(novelId).append(":").append(chapterNumber);
        for (Object param : params) {
            key.append(":").append(param);
        }
        return key.toString();
    }
    
    /**
     * 失效小说的所有缓存
     */
    public void invalidateNovel(Long novelId) {
        String prefix = "novel:" + novelId;
        cache.keySet().removeIf(key -> key.contains(":" + novelId + ":"));
        logger.info("失效小说缓存: novelId={}", novelId);
    }
    
    /**
     * 失效特定章节之后的缓存
     */
    public void invalidateAfterChapter(Long novelId, Integer chapterNumber) {
        cache.keySet().removeIf(key -> {
            if (!key.contains(":" + novelId + ":")) {
                return false;
            }
            
            // 提取章节号（简单实现）
            try {
                String[] parts = key.split(":");
                if (parts.length >= 3) {
                    int cachedChapter = Integer.parseInt(parts[2]);
                    return cachedChapter >= chapterNumber;
                }
            } catch (Exception e) {
                // 忽略
            }
            
            return false;
        });
        
        logger.info("失效缓存: novelId={}, chapter>={}", novelId, chapterNumber);
    }
    
    /**
     * 定时清理过期缓存
     */
    @Scheduled(fixedRate = 10 * 60 * 1000) // 每10分钟
    public void cleanExpiredCache() {
        long now = System.currentTimeMillis();
        int removedCount = 0;
        
        Iterator<Map.Entry<String, CacheEntry>> iterator = cache.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, CacheEntry> entry = iterator.next();
            if (now - entry.getValue().timestamp > CACHE_TTL) {
                iterator.remove();
                removedCount++;
            }
        }
        
        if (removedCount > 0) {
            logger.info("清理过期缓存: 移除{}个条目", removedCount);
        }
    }
    
    /**
     * 获取缓存统计
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalEntries", cache.size());
        
        long now = System.currentTimeMillis();
        long validCount = cache.values().stream()
            .filter(e -> now - e.timestamp <= CACHE_TTL)
            .count();
        
        stats.put("validEntries", validCount);
        stats.put("expiredEntries", cache.size() - validCount);
        
        return stats;
    }
    
    /**
     * 缓存条目
     */
    private static class CacheEntry {
        long timestamp;
        Object data;
    }
}


