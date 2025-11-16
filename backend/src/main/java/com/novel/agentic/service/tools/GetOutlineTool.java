package com.novel.agentic.service.tools;

import com.novel.agentic.model.ToolDefinition;
import com.novel.domain.entity.Novel;
import com.novel.domain.entity.NovelOutline;
import com.novel.repository.NovelRepository;
import com.novel.repository.NovelOutlineRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 获取小说大纲工具
 * 优先返回核心设定（避免AI上帝视角），如果核心设定不存在则返回完整大纲
 */
@Component
public class GetOutlineTool implements Tool {

    private static final Logger logger = LoggerFactory.getLogger(GetOutlineTool.class);

    @Autowired
    private NovelRepository novelRepository;

    @Autowired
    private NovelOutlineRepository outlineRepository;

    @Autowired
    private ToolRegistry registry;
    
    @PostConstruct
    public void init() {
        registry.register(this);
    }
    
    @Override
    public String getName() {
        return "getOutline";
    }
    
    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");

        Map<String, Object> novelIdProp = new HashMap<>();
        novelIdProp.put("type", "integer");
        novelIdProp.put("description", "小说ID");

        Map<String, Object> properties = new HashMap<>();
        properties.put("novelId", novelIdProp);

        params.put("properties", properties);
        params.put("required", new String[]{"novelId"});

        return ToolDefinition.builder()
            .name(getName())
            .description("获取小说的核心设定信息（世界观、力量体系、角色基本设定、写作风格等），不包含具体剧情发展，避免AI产生上帝视角。")
            .parameters(params)
            .returnExample("{\"coreSettings\": \"世界观：...\\n力量体系：...\", \"wordCount\": 3000}")
            .costEstimate(500)
            .required(true)
            .build();
    }
    
    @Override
    public Object execute(Map<String, Object> args) throws Exception {
        Long novelId = ((Number) args.get("novelId")).longValue();

        Novel novel = novelRepository.selectById(novelId);
        if (novel == null) {
            throw new IllegalArgumentException("小说不存在: " + novelId);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("title", novel.getTitle());

        // 优先使用核心设定（避免AI上帝视角）
        Optional<NovelOutline> outlineOpt = outlineRepository.findByNovelId(novelId);
        if (outlineOpt.isPresent()) {
            NovelOutline outline = outlineOpt.get();
            String coreSettings = outline.getCoreSettings();

            if (coreSettings != null && !coreSettings.trim().isEmpty()) {
                // 使用核心设定（推荐）
                result.put("coreSettings", coreSettings);
                result.put("wordCount", coreSettings.length());
                result.put("type", "core_settings");
                logger.info("✅ 返回核心设定: novelId={}, 长度={}", novelId, coreSettings.length());
            } else {
                // 核心设定未提炼，降级使用完整大纲
                String fullOutline = novel.getOutline() != null ? novel.getOutline() : "暂无大纲";
                result.put("coreSettings", fullOutline);
                result.put("wordCount", fullOutline.length());
                result.put("type", "full_outline_fallback");
                logger.warn("⚠️ 核心设定未提炼，降级使用完整大纲: novelId={}", novelId);
            }
        } else {
            // 没有大纲记录，使用 novels.outline
            String fullOutline = novel.getOutline() != null ? novel.getOutline() : "暂无大纲";
            result.put("coreSettings", fullOutline);
            result.put("wordCount", fullOutline.length());
            result.put("type", "novel_outline_fallback");
            logger.warn("⚠️ 未找到大纲记录，使用 novels.outline: novelId={}", novelId);
        }

        return result;
    }
}

