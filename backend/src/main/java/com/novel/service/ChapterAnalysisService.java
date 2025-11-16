package com.novel.service;

import com.novel.entity.ChapterAnalysis;
import com.novel.domain.entity.Chapter;
import com.novel.mapper.ChapterAnalysisMapper;
import com.novel.repository.ChapterRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChapterAnalysisService {

    @Autowired
    private ChapterAnalysisMapper chapterAnalysisMapper;

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private AIWritingService aiWritingService;

    public ChapterAnalysis createAnalysis(ChapterAnalysis analysis) {
        chapterAnalysisMapper.insert(analysis);
        return analysis;
    }

    public ChapterAnalysis getAnalysisById(Long id) {
        return chapterAnalysisMapper.findById(id);
    }

    public List<ChapterAnalysis> getAnalysesByNovelId(Long novelId) {
        return chapterAnalysisMapper.findByNovelId(novelId);
    }

    public ChapterAnalysis analyzeChapters(Long novelId, String analysisType, Integer startChapter, Integer endChapter) {
        // 先检查是否已有相同的分析
        ChapterAnalysis existing = chapterAnalysisMapper.findLatestByTypeAndRange(
            novelId, analysisType, startChapter, endChapter
        );
        
        if (existing != null) {
            log.info("找到已存在的分析记录: {}", existing.getId());
            return existing;
        }

        // 获取指定范围的章节内容（按章节号升序）
        List<Chapter> chapters = chapterRepository.findByNovelOrderByChapterNumberAsc(novelId).stream()
            .filter(c -> c.getChapterNumber() != null && 
                         c.getChapterNumber() >= startChapter && 
                         c.getChapterNumber() <= endChapter)
            .collect(Collectors.toList());

        if (chapters.isEmpty()) {
            throw new RuntimeException("未找到指定范围的章节");
        }

        // 组合章节内容
        StringBuilder contentBuilder = new StringBuilder();
        for (Chapter chapter : chapters) {
            contentBuilder.append("## ").append(chapter.getTitle()).append("\n\n");
            contentBuilder.append(chapter.getContent()).append("\n\n");
        }
        String combinedContent = contentBuilder.toString();

        // 根据分析类型生成提示词
        String prompt = generateAnalysisPrompt(analysisType, combinedContent, startChapter, endChapter);

        // 调用AI进行分析
        String analysisContent = aiWritingService.generateContent(prompt, "chapter_analysis");

        // 保存分析结果
        ChapterAnalysis analysis = new ChapterAnalysis();
        analysis.setNovelId(novelId);
        analysis.setAnalysisType(analysisType);
        analysis.setStartChapter(startChapter);
        analysis.setEndChapter(endChapter);
        analysis.setAnalysisContent(analysisContent);
        analysis.setWordCount(analysisContent.replaceAll("\\s+", "").length());

        chapterAnalysisMapper.insert(analysis);
        log.info("创建章节分析记录: {}, 类型: {}", analysis.getId(), analysisType);

        return analysis;
    }

    private String generateAnalysisPrompt(String analysisType, String content, int start, int end) {
        String basePrompt = String.format("请分析第%d章到第%d章的以下内容：\n\n%s\n\n", start, end, content);

        switch (analysisType) {
            case "golden_three":
                return basePrompt + "请进行黄金三章分析，包括：\n" +
                       "1. 开篇吸引力分析\n" +
                       "2. 人物塑造和世界观展现\n" +
                       "3. 悬念设置和节奏把控\n" +
                       "4. 读者留存关键点\n" +
                       "5. 优化建议";

            case "main_plot":
                return basePrompt + "请分析主线剧情，包括：\n" +
                       "1. 核心故事线梳理\n" +
                       "2. 主要情节点和转折\n" +
                       "3. 剧情推进节奏\n" +
                       "4. 冲突设置和解决\n" +
                       "5. 剧情连贯性评估";

            case "sub_plot":
                return basePrompt + "请分析支线剧情，包括：\n" +
                       "1. 支线故事梳理\n" +
                       "2. 与主线的关联度\n" +
                       "3. 辅助人物发展\n" +
                       "4. 世界观扩展作用\n" +
                       "5. 优化建议";

            case "theme":
                return basePrompt + "请进行主题分析，包括：\n" +
                       "1. 核心主题识别\n" +
                       "2. 价值观表达\n" +
                       "3. 深层含义挖掘\n" +
                       "4. 主题表现手法\n" +
                       "5. 读者共鸣点";

            case "character":
                return basePrompt + "请进行角色分析，包括：\n" +
                       "1. 主要角色性格特征\n" +
                       "2. 人物关系网络\n" +
                       "3. 角色成长轨迹\n" +
                       "4. 人物塑造手法\n" +
                       "5. 优化建议";

            case "worldbuilding":
                return basePrompt + "请分析世界设定，包括：\n" +
                       "1. 世界观架构\n" +
                       "2. 设定系统完整性\n" +
                       "3. 规则合理性\n" +
                       "4. 背景展现方式\n" +
                       "5. 优化建议";

            case "writing_style":
                return basePrompt + "请分析写作风格与技巧，包括：\n" +
                       "1. 叙事视角和语言风格\n" +
                       "2. 描写手法运用\n" +
                       "3. 对话设计质量\n" +
                       "4. 节奏控制技巧\n" +
                       "5. 文笔优化建议";

            default:
                return basePrompt + "请进行全面分析";
        }
    }

    public void deleteAnalysis(Long id) {
        chapterAnalysisMapper.deleteById(id);
    }

    public void deleteByNovelId(Long novelId) {
        chapterAnalysisMapper.deleteByNovelId(novelId);
    }
}

