package com.novel.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.entity.QimaoCategory;
import com.novel.mapper.QimaoCategoryMapper;
import com.novel.mapper.QimaoScraperConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;

/**
 * 七猫爬虫定时任务服务
 */
@Slf4j
@Service
public class QimaoScheduledService {

    @Autowired
    private QimaoScraperService qimaoScraperService;

    @Autowired
    private QimaoCategoryMapper qimaoCategoryMapper;

    @Autowired
    private QimaoScraperConfigMapper configMapper;

    /**
     * 每小时检查一次是否需要爬取
     * 在配置的时间段内（如22:00-04:00），随机选择一个时间执行
     */
    @Scheduled(cron = "0 0 * * * ?") // 每小时整点执行
    public void checkAndScrape() {
        try {
            // 检查是否启用自动爬取
            String enableAutoScrape = configMapper.getConfigValue("enable_auto_scrape");
            if (!"true".equalsIgnoreCase(enableAutoScrape)) {
                log.debug("自动爬取未启用");
                return;
            }

            // 检查是否在允许的时间段内
            if (!isInScrapeTimeWindow()) {
                log.debug("当前时间不在爬取时间窗口内");
                return;
            }

            // 获取需要爬取的分类
            List<QimaoCategory> categoriesToScrape = getCategoriesToScrape();
            
            if (categoriesToScrape.isEmpty()) {
                log.info("没有需要爬取的分类");
                return;
            }

            // 随机选择一个分类进行爬取
            QimaoCategory category = categoriesToScrape.get(
                new Random().nextInt(categoriesToScrape.size())
            );

            log.info("开始自动爬取分类: {}", category.getCategoryName());
            
            // 获取最大页数配置
            String maxPagesStr = configMapper.getConfigValue("max_pages_per_category");
            int maxPages = maxPagesStr != null ? Integer.parseInt(maxPagesStr) : 3;

            // 执行爬取
            qimaoScraperService.scrapeCategory(category.getCategoryCode(), maxPages);

            // 更新分类的爬取时间和次数
            category.setLastScrapeTime(LocalDateTime.now());
            category.setScrapeCount((category.getScrapeCount() != null ? category.getScrapeCount() : 0) + 1);
            qimaoCategoryMapper.updateById(category);

            log.info("自动爬取任务已启动: {}", category.getCategoryName());

        } catch (Exception e) {
            log.error("自动爬取检查失败", e);
        }
    }

    /**
     * 检查当前时间是否在爬取时间窗口内
     */
    private boolean isInScrapeTimeWindow() {
        try {
            String startTimeStr = configMapper.getConfigValue("scrape_time_start");
            String endTimeStr = configMapper.getConfigValue("scrape_time_end");

            if (startTimeStr == null || endTimeStr == null) {
                return false;
            }

            LocalTime now = LocalTime.now();
            LocalTime startTime = LocalTime.parse(startTimeStr, DateTimeFormatter.ofPattern("HH:mm"));
            LocalTime endTime = LocalTime.parse(endTimeStr, DateTimeFormatter.ofPattern("HH:mm"));

            // 处理跨天的情况（如22:00-04:00）
            if (startTime.isAfter(endTime)) {
                return now.isAfter(startTime) || now.isBefore(endTime);
            } else {
                return now.isAfter(startTime) && now.isBefore(endTime);
            }
        } catch (Exception e) {
            log.error("解析爬取时间窗口失败", e);
            return false;
        }
    }

    /**
     * 获取需要爬取的分类列表
     * 规则：距离上次爬取超过配置的间隔时间
     */
    private List<QimaoCategory> getCategoriesToScrape() {
        try {
            String intervalHoursStr = configMapper.getConfigValue("scrape_interval_hours");
            int intervalHours = intervalHoursStr != null ? Integer.parseInt(intervalHoursStr) : 24;

            LocalDateTime threshold = LocalDateTime.now().minusHours(intervalHours);

            QueryWrapper<QimaoCategory> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("is_active", true);
            queryWrapper.and(wrapper -> wrapper
                .isNull("last_scrape_time")
                .or()
                .lt("last_scrape_time", threshold)
            );

            return qimaoCategoryMapper.selectList(queryWrapper);
        } catch (Exception e) {
            log.error("获取待爬取分类失败", e);
            return List.of();
        }
    }

    /**
     * 手动触发爬取检查（用于测试）
     */
    public void manualTrigger() {
        log.info("手动触发爬取检查");
        checkAndScrape();
    }
}
