package com.novel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.domain.entity.Novel;
import com.novel.domain.entity.NovelCharacterProfile;
import com.novel.domain.entity.NovelChronicle;
import com.novel.domain.entity.NovelForeshadowing;
import com.novel.domain.entity.Chapter;
import com.novel.domain.entity.ChapterSummary;
import com.novel.domain.entity.NovelWorldDictionary;
import com.novel.repository.NovelCharacterProfileRepository;
import com.novel.repository.NovelChronicleRepository;
import com.novel.repository.NovelForeshadowingRepository;
import com.novel.repository.ChapterRepository;
import com.novel.repository.ChapterSummaryRepository;
import com.novel.repository.NovelWorldDictionaryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 小说记忆库服务
 * 负责从数据库装配记忆库数据，提供给AI写作使用
 */
@Service
public class NovelMemoryService {

    private static final Logger logger = LoggerFactory.getLogger(NovelMemoryService.class);



/**
     * 根据章节号生成章节规划
     * 这里可以根据小说大纲或其他逻辑生成章节规划
     */
    public Map<String, Object> generateChapterPlan(Long novelId, Integer chapterNumber) {
        logger.info("📋 生成章节规划: 小说ID={}, 章节={}", novelId, chapterNumber);

        Map<String, Object> chapterPlan = new HashMap<>();
        chapterPlan.put("chapterNumber", chapterNumber);
        chapterPlan.put("title", "第" + chapterNumber + "章");
        chapterPlan.put("type", "剧情推进");
        chapterPlan.put("estimatedWords", 3000);
        chapterPlan.put("focus", "推进主线剧情");
        chapterPlan.put("keyPoints", Arrays.asList("情节发展", "角色互动", "伏笔设置"));

        return chapterPlan;
    }
}