package com.novel.agentic.service.tools;

import com.novel.agentic.model.ToolDefinition;
import com.novel.domain.entity.Chapter;
import com.novel.service.ChapterService;
import com.novel.service.ChapterSummaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 获取最近章节内容工具（固定上下文）
 * 
 * 返回：
 * 1. 最近3章完整内容（保持连贯性）
 * 2. 最近30章概括（了解剧情发展）
 */
@Component
public class GetRecentChaptersTool implements Tool {
    
    @Autowired
    private ChapterService chapterService;
    
    @Autowired(required = false)
    private ChapterSummaryService chapterSummaryService;
    
    @Autowired
    private ToolRegistry registry;
    
    @PostConstruct
    public void init() {
        registry.register(this);
    }
    
    @Override
    public String getName() {
        return "getRecentChapters";
    }
    
    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        
        Map<String, Object> novelIdProp = new HashMap<>();
        novelIdProp.put("type", "integer");
        novelIdProp.put("description", "小说ID");
        
        Map<String, Object> currentChapterProp = new HashMap<>();
        currentChapterProp.put("type", "integer");
        currentChapterProp.put("description", "当前要生成的章节号");
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("novelId", novelIdProp);
        properties.put("currentChapter", currentChapterProp);
        
        params.put("properties", properties);
        params.put("required", new String[]{"novelId", "currentChapter"});
        
        return ToolDefinition.builder()
            .name(getName())
            .description("【固定上下文】获取最近3章完整内容 + 更早章节的概括（不重复）。完整内容用于保持写作连贯性，概括用于了解整体剧情发展。")
            .parameters(params)
            .returnExample("{\"recentFullChapters\": [...], \"recentSummaries\": [...], \"summaryRange\": \"第X章-第Y章\", \"fullChapterRange\": \"第X章-第Y章\"}")
            .costEstimate(2000)
            .required(true)  // 标记为必查
            .build();
    }
    
    @Override
    public Object execute(Map<String, Object> args) throws Exception {
        Long novelId = ((Number) args.get("novelId")).longValue();
        
        // 兼容两种参数名：currentChapter 或 chapterNumber
        Integer currentChapter;
        if (args.containsKey("currentChapter")) {
            currentChapter = ((Number) args.get("currentChapter")).intValue();
        } else if (args.containsKey("chapterNumber")) {
            currentChapter = ((Number) args.get("chapterNumber")).intValue();
        } else {
            throw new IllegalArgumentException("缺少参数：currentChapter 或 chapterNumber");
        }
        
        Map<String, Object> result = new HashMap<>();
        
        // 第1部分：最近3章完整内容
        List<Chapter> recentChapters = chapterService.getRecentChapters(novelId, currentChapter, 3);
        List<Map<String, Object>> recentFullChapters = recentChapters.stream()
            .map(chapterEntity -> {
                Map<String, Object> chapter = new HashMap<>();
                chapter.put("chapterNumber", chapterEntity.getChapterNumber());
                chapter.put("title", chapterEntity.getTitle() != null ? chapterEntity.getTitle() : "第" + chapterEntity.getChapterNumber() + "章");
                String content = chapterEntity.getContent() != null ? chapterEntity.getContent() : "";
                chapter.put("content", content);
                chapter.put("wordCount", content.length());
                return chapter;
            })
            .collect(Collectors.toList());
        
        result.put("recentFullChapters", recentFullChapters);
        
        // 🔧 计算完整章节的范围，用于显示给AI
        Integer fullChapterStart = null;
        Integer fullChapterEnd = null;
        if (!recentFullChapters.isEmpty()) {
            fullChapterStart = (Integer) recentFullChapters.get(recentFullChapters.size() - 1).get("chapterNumber");
            fullChapterEnd = (Integer) recentFullChapters.get(0).get("chapterNumber");
        }
        
        // 第2部分：更早章节的概括（排除已有完整内容的章节）
        // 例如：写第11章时，完整内容是8、9、10章，那么概括就获取1-7章
        List<Map<String, Object>> recentSummaries = new ArrayList<>();
        Integer summaryEndChapter = fullChapterStart != null ? fullChapterStart - 1 : currentChapter - 4;
        
        // 🆕 支持可配置的摘要数量
        int summaryLimit = 30; // 默认30章
        if (args.containsKey("summaryLimit")) {
            summaryLimit = ((Number) args.get("summaryLimit")).intValue();
        }
        Integer summaryStartChapter = Math.max(1, summaryEndChapter - (summaryLimit - 1));
        
        if (summaryEndChapter >= summaryStartChapter && chapterSummaryService != null) {
            try {
                List<String> summaryTexts = chapterSummaryService.getRecentChapterSummaries(
                    novelId, summaryEndChapter + 1, summaryEndChapter - summaryStartChapter + 1);
                
                for (int i = 0; i < summaryTexts.size(); i++) {
                    Map<String, Object> s = new HashMap<>();
                    s.put("chapterNumber", summaryStartChapter + i);
                    s.put("summary", summaryTexts.get(i));
                    recentSummaries.add(s);
                }
            } catch (Exception e) {
                // 降级：从Chapter生成简化摘要
                recentSummaries = generateSimpleSummariesInRange(novelId, summaryStartChapter, summaryEndChapter);
            }
        } else if (summaryEndChapter >= summaryStartChapter) {
            // 降级：从Chapter生成简化摘要
            recentSummaries = generateSimpleSummariesInRange(novelId, summaryStartChapter, summaryEndChapter);
        }
        
        result.put("recentSummaries", recentSummaries);
        
        // 🔧 添加范围说明，让AI清楚知道上下文结构
        if (fullChapterStart != null && fullChapterEnd != null) {
            result.put("fullChapterRange", "第" + fullChapterStart + "章-第" + fullChapterEnd + "章");
        }
        if (!recentSummaries.isEmpty()) {
            result.put("summaryRange", "第" + summaryStartChapter + "章-第" + summaryEndChapter + "章");
        }
        
        return result;
    }
    
    /**
     * 生成简化摘要（降级方案）- 按范围获取
     */
    private List<Map<String, Object>> generateSimpleSummariesInRange(Long novelId, Integer startChapter, Integer endChapter) {
        List<Map<String, Object>> summaries = new ArrayList<>();
        
        for (int i = startChapter; i <= endChapter; i++) {
            Chapter chapter = chapterService.getChapterByNovelAndNumber(novelId, i);
            if (chapter != null) {
                Map<String, Object> summary = new HashMap<>();
                summary.put("chapterNumber", chapter.getChapterNumber());
                
                String content = chapter.getContent() != null ? chapter.getContent() : "";
                String simpleSummary = content.length() > 200 ? content.substring(0, 200) + "..." : content;
                summary.put("summary", simpleSummary);
                
                summaries.add(summary);
            }
        }
        
        return summaries;
    }
    
    /**
     * 生成简化摘要（降级方案）- 旧版保留以防调用
     * @deprecated 建议使用 generateSimpleSummariesInRange
     */
    @Deprecated
    private List<Map<String, Object>> generateSimpleSummaries(Long novelId, Integer currentChapter, int count) {
        List<Chapter> chapters = chapterService.getRecentChapters(novelId, currentChapter, count);

        return chapters.stream()
            .map(chapter -> {
                Map<String, Object> summary = new HashMap<>();
                summary.put("chapterNumber", chapter.getChapterNumber());

                String content = chapter.getContent() != null ? chapter.getContent() : "";
                String simpleSummary = content.length() > 200 ? content.substring(0, 200) + "..." : content;
                summary.put("summary", simpleSummary);
                
                return summary;
            })
            .collect(Collectors.toList());
    }
    
}

