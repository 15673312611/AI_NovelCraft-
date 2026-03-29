package com.novel.agentic.service.tools;

import com.novel.agentic.model.ToolDefinition;
import com.novel.domain.entity.Novel;
import com.novel.domain.entity.NovelOutline;
import com.novel.repository.NovelOutlineRepository;
import com.novel.repository.NovelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 获取小说写作链路所需的大纲上下文。
 * 默认返回精简后的 coreSettings；当 mode=FULL 时优先返回完整大纲。
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

        Map<String, Object> modeProp = new HashMap<>();
        modeProp.put("type", "string");
        modeProp.put("description", "大纲模式：SIMPLIFIED 或 FULL，默认 SIMPLIFIED");

        Map<String, Object> properties = new HashMap<>();
        properties.put("novelId", novelIdProp);
        properties.put("mode", modeProp);

        params.put("properties", properties);
        params.put("required", new String[]{"novelId"});

        return ToolDefinition.builder()
            .name(getName())
            .description("获取小说写作所需的大纲上下文。默认返回精简核心设定；FULL 模式优先返回完整大纲。")
            .parameters(params)
            .returnExample("{\"coreSettings\":\"世界观：...\",\"fullOutline\":\"...\",\"resolvedMode\":\"SIMPLIFIED\",\"type\":\"core_settings\"}")
            .costEstimate(500)
            .required(true)
            .build();
    }

    @Override
    public Object execute(Map<String, Object> args) throws Exception {
        Long novelId = ((Number) args.get("novelId")).longValue();
        String requestedMode = resolveMode(args.get("mode"));

        Novel novel = novelRepository.selectById(novelId);
        if (novel == null) {
            throw new IllegalArgumentException("小说不存在: " + novelId);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("title", novel.getTitle());
        result.put("requestedMode", requestedMode);

        Optional<NovelOutline> outlineOpt = outlineRepository.findByNovelId(novelId);
        NovelOutline outline = outlineOpt.orElse(null);
        String coreSettings = outline != null ? safeTrim(outline.getCoreSettings()) : "";

        if (Novel.OUTLINE_CONTEXT_MODE_FULL.equals(requestedMode)) {
            String fullOutline = buildFullOutline(novel, outline);
            if (StringUtils.hasText(fullOutline)) {
                result.put("coreSettings", coreSettings);
                result.put("fullOutline", fullOutline);
                result.put("wordCount", fullOutline.length());
                result.put("resolvedMode", Novel.OUTLINE_CONTEXT_MODE_FULL);
                result.put("type", "full_outline");
                logger.info("返回完整大纲: novelId={}, length={}", novelId, fullOutline.length());
                return result;
            }

            logger.warn("完整大纲缺失，回退到精简模式: novelId={}", novelId);
            result.put("message", "完整大纲缺失，已自动回退到精简模式。");
        }

        if (StringUtils.hasText(coreSettings)) {
            result.put("coreSettings", coreSettings);
            result.put("wordCount", coreSettings.length());
            result.put("resolvedMode", Novel.OUTLINE_CONTEXT_MODE_SIMPLIFIED);
            result.put("type", "core_settings");
            logger.info("返回核心设定: novelId={}, length={}", novelId, coreSettings.length());
            return result;
        }

        result.put("coreSettings", "");
        result.put("wordCount", 0);
        result.put("resolvedMode", Novel.OUTLINE_CONTEXT_MODE_SIMPLIFIED);

        if (outline != null) {
            result.put("type", "core_settings_pending");
            result.put("message", "核心设定尚未提炼完成，正文链路将使用空设定。");
            logger.warn("核心设定缺失: novelId={}", novelId);
        } else {
            result.put("type", "outline_missing");
            result.put("message", "未找到大纲记录，正文链路将使用空设定。");
            logger.warn("未找到大纲记录: novelId={}", novelId);
        }

        return result;
    }

    private String resolveMode(Object rawMode) {
        String mode = rawMode == null ? null : String.valueOf(rawMode).trim().toUpperCase();
        if (Novel.OUTLINE_CONTEXT_MODE_FULL.equals(mode)) {
            return Novel.OUTLINE_CONTEXT_MODE_FULL;
        }
        return Novel.OUTLINE_CONTEXT_MODE_SIMPLIFIED;
    }

    private String buildFullOutline(Novel novel, NovelOutline outline) {
        String novelOutline = safeTrim(novel.getOutline());
        if (StringUtils.hasText(novelOutline)) {
            return novelOutline;
        }
        if (outline == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        appendSection(sb, "标题", outline.getTitle());
        appendSection(sb, "基础构思", outline.getBasicIdea());
        appendSection(sb, "核心主题", outline.getCoreTheme());
        appendSection(sb, "主要角色", outline.getMainCharacters());
        appendSection(sb, "剧情结构", outline.getPlotStructure());
        appendSection(sb, "世界设定", outline.getWorldSetting());
        appendSection(sb, "关键元素", outline.getKeyElements());
        appendSection(sb, "冲突类型", outline.getConflictTypes());
        appendSection(sb, "核心设定", outline.getCoreSettings());
        return sb.toString().trim();
    }

    private void appendSection(StringBuilder sb, String title, String content) {
        String normalized = safeTrim(content);
        if (!StringUtils.hasText(normalized)) {
            return;
        }
        if (sb.length() > 0) {
            sb.append("\n\n");
        }
        sb.append("【").append(title).append("】\n").append(normalized);
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
