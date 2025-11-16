package com.novel.agentic.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service("agenticPromptTemplateService")
public class PromptTemplateService {

    private static final Logger logger = LoggerFactory.getLogger(PromptTemplateService.class);
    private static final String CLASSPATH_PREFIX = "classpath:prompts_output/";
    private static final Path[] FALLBACK_DIRS = new Path[] {
        Paths.get("prompts_output"),
        Paths.get("backend", "prompts_output"),
        Paths.get("..", "backend", "prompts_output")
    };

    private final ResourceLoader resourceLoader;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @Autowired
    public PromptTemplateService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public String loadTemplate(String fileName) {
        return cache.computeIfAbsent(fileName, this::readTemplateInternal);
    }

    private String readTemplateInternal(String fileName) {
        // 优先使用classpath资源
        try {
            Resource resource = resourceLoader.getResource(CLASSPATH_PREFIX + fileName);
            if (resource.exists()) {
                return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            logger.warn("从classpath加载提示词失败: {}", fileName, e);
        }

        // 回退到工程目录
        for (Path dir : FALLBACK_DIRS) {
            try {
                Path path = dir.resolve(fileName);
                if (Files.exists(path)) {
                    return Files.readString(path, StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                logger.error("从fallback目录加载提示词失败: {} -> {}", fileName, dir, e);
            }
        }

        throw new IllegalArgumentException("无法加载提示词模板: " + fileName);
    }
}

