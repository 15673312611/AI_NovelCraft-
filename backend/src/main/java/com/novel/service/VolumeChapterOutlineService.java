package com.novel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.domain.entity.*;
import com.novel.dto.AIConfigRequest;
import com.novel.mapper.NovelVolumeMapper;
import com.novel.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 卷级批量章纲生成服务
 * - 根据：全书大纲 + 本卷蓝图 + 历史伏笔池
 * - 一次性生成本卷的 N 个章纲（默认50）
 * - 返回内存结果（不落库），同时可返回 react_decision_log 供排错
 */
@Service
public class VolumeChapterOutlineService {

    private static final Logger logger = LoggerFactory.getLogger(VolumeChapterOutlineService.class);

    @Autowired
    private NovelVolumeMapper volumeMapper;

    @Autowired
    private NovelRepository novelRepository;

    @Autowired
    private NovelOutlineRepository outlineRepository;

    @Autowired
    private NovelForeshadowingRepository foreshadowingRepository;

    @Autowired
    private VolumeChapterOutlineRepository outlineRepo;

    @Autowired
    private ForeshadowLifecycleLogRepository lifecycleLogRepo;

    @Autowired
    private AIWritingService aiWritingService;

    private final ObjectMapper mapper = new ObjectMapper();

    @Transactional
    public Map<String, Object> generateOutlinesForVolume(Long volumeId, Integer count, AIConfigRequest aiConfig) {
        NovelVolume volume = volumeMapper.selectById(volumeId);
        if (volume == null) {
            throw new RuntimeException("卷不存在: " + volumeId);
        }
        if (count == null || count <= 0) {
            int computed = 0;
            try { computed = volume.getChapterCount(); } catch (Exception ignore) {}
            count = computed > 0 ? computed : 50;
        }
        Novel novel = novelRepository.selectById(volume.getNovelId());
        if (novel == null) {
            throw new RuntimeException("小说不存在: " + volume.getNovelId());
        }
        NovelOutline superOutline = outlineRepository.findByNovelIdAndStatus(
                volume.getNovelId(), NovelOutline.OutlineStatus.CONFIRMED).orElse(null);
        if (superOutline == null || isBlank(superOutline.getPlotStructure())) {
            throw new RuntimeException("缺少已确认的全书大纲(plotStructure)");
        }

        // 历史未回收伏笔池（ACTIVE）
        List<NovelForeshadowing> unresolved = foreshadowingRepository.findByNovelIdAndStatus(
                volume.getNovelId(), "ACTIVE");

        String prompt = buildPrompt(novel, volume, superOutline, unresolved, count);
        List<Map<String, String>> messages = buildMessages(prompt);

        logger.info("🤖 调用AI批量生成卷章纲，volumeId={}, count={}, promptLen={}", volumeId, count, prompt.length());

        String raw;
        try {
            raw = aiWritingService.generateContentWithMessages(messages, "volume_chapter_outlines_generation", aiConfig);
        } catch (Exception e) {
            logger.error("AI生成卷章纲失败: {}", e.getMessage(), e);
            throw new RuntimeException("AI服务调用失败: " + e.getMessage());
        }

        // 解析 JSON（失败则直接抛异常，不删除旧数据）
        String json = extractPureJson(raw);
        List<Map<String, Object>> outlines;
        try {
            outlines = mapper.readValue(json, new TypeReference<List<Map<String, Object>>>(){});
        } catch (Exception e) {
            logger.warn("JSON解析失败，尝试替换中文引号后重试: {}", e.getMessage());
            String fixed = json
                    .replace('\u201C', '"')
                    .replace('\u201D', '"')
                    .replace('\u2018', '\'')
                    .replace('\u2019', '\'');
            try {
                outlines = mapper.readValue(fixed, new TypeReference<List<Map<String, Object>>>(){});
            } catch (Exception e2) {
                logger.error("❌ 解析卷章纲失败: {}\n原始响应(前500)：{}", e2.getMessage(), raw.substring(0, Math.min(500, raw.length())));
                throw new RuntimeException("解析卷章纲失败，请检查AI返回格式: " + e2.getMessage());
            }
        }

        // 验证生成数量
        if (outlines == null || outlines.isEmpty()) {
            logger.error("❌ AI返回空章纲列表");
            throw new RuntimeException("AI返回空章纲列表，生成失败");
        }
        logger.info("✅ AI生成章纲成功: volumeId={}, 实际生成{}章", volumeId, outlines.size());

        // 附带决策日志
        String reactDecisionLog = buildDecisionLog(novel, volume, superOutline, unresolved, prompt, raw, count);

        // 入库：保存章纲 + 伏笔生命周期日志（失败则抛异常，触发事务回滚）
        persistOutlines(volume, outlines, reactDecisionLog);
        logger.info("✅ 卷章纲已入库: volumeId={}, count={}", volumeId, outlines.size());

        // 只有完全成功才返回结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("volumeId", volumeId);
        result.put("novelId", volume.getNovelId());
        result.put("count", outlines.size());
        result.put("outlines", outlines);
        result.put("react_decision_log", reactDecisionLog);
        return result;
    }

    private List<Map<String, String>> buildMessages(String prompt) {
        List<Map<String, String>> msgs = new ArrayList<>();
        msgs.add(msg("system", "你是资深长篇小说/网文的编剧与结构设计专家，擅长节奏控制、反预期设计与伏笔管理。严格输出纯净JSON，不包含任何多余说明；遵守知识边界与世界规则；任何揭露必须有前文锚点支撑，否则降级为加深。"));
        msgs.add(msg("user", prompt));
        return msgs;
    }

    private Map<String, String> msg(String role, String content) {
        Map<String, String> m = new HashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    private String buildPrompt(Novel novel, NovelVolume volume, NovelOutline superOutline,
                               List<NovelForeshadowing> unresolved, int count) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 角色\n")
          .append("你是一名拥有十年创作经验、精通市场分析的网文金牌编辑兼爆款作家,你的笔名是“墨染江湖”。你深谙爽点设计、黄金三章、人物弧光、节奏把控等核心技巧，同时对玄幻、都市、科幻、仙侠、女频、悬疑等主流题材了如指掌 剧情要有意思不能太按部就班。。你的目标：为当前卷一次性生成").append(count)
          .append("个章纲，保证跌宕起伏、反套路、人设立体，并合理地“埋/提/加深/回收”伏笔。\n\n");

        sb.append("# 小说信息\n")
          .append("- 标题：").append(s(novel.getTitle())).append("\n")
          .append("- 简介/构思：").append(s(novel.getDescription())).append("\n\n");

        sb.append("# 全书大纲（精华）\n").append(s(limit(superOutline.getPlotStructure(), 6000))).append("\n\n");

        sb.append("# 本卷信息\n")
          .append("- 卷序：第").append(nz(volume.getVolumeNumber(), "?"))
          .append("卷\n")
          .append("- 卷名：").append(s(volume.getTitle())).append("\n")
          .append("- 主题：").append(s(volume.getTheme())).append("\n")
          .append("- 卷蓝图（contentOutline）：\n").append(s(limit(volume.getContentOutline(), 4000))).append("\n")
          .append("- 章节范围：").append(volume.getChapterStart() != null && volume.getChapterEnd() != null
                    ? ("第" + volume.getChapterStart() + "-" + volume.getChapterEnd() + "章") : "未指定").append("\n\n");

        sb.append("# 历史未回收伏笔池（供决策）\n");
        if (unresolved != null && !unresolved.isEmpty()) {
            int shown = 0;
            for (NovelForeshadowing f : unresolved) {
                if (shown++ >= 30) break; // 控制长度
                sb.append("- [#").append(f.getId()).append("] 优先级").append(nz(f.getPriority(), 0))
                  .append(" | 植入章节=").append(nz(f.getPlantedChapter(), 0))
                  .append(" | 内容：").append(s(limit(f.getContent(), 200))).append("\n");
            }
        } else {
            sb.append("- （无）\n");
        }
        sb.append("\n");

        sb.append("# 章纲生成目标\n")
          .append("- 数量：恰好").append(count).append("章（不可多也不可少）\n")
          .append("- 节奏：必须有起承转合与波峰，至少30%章节含反转/意外；高潮与翻盘要穿插。\n")
          .append("- 人设：强化人物动机与内在冲突，兼顾支线与成长弧。\n")
          .append("- 反套路：避免“读者一眼看穿”的直线发展，注意因果闭环。\n")
          .append("- 通用性：适用于都市/奇幻/科幻/历史/仙侠/言情/玄幻等多类型长篇叙事，避免类型专属套路的绑定。\n")
          .append("- 知识边界与世界规则：不得让角色知道其不应知道的信息；不得临时创造关键世界规则。若存在不确定性，用PLANT/DEEPEN而非RESOLVE。\n")
          .append("- 伏笔管理：允许四类动作——PLANT(埋)、REFERENCE(提及提醒)、DEEPEN(加深推进)、RESOLVE(回收)。\n")
          .append("  - 若本卷伏笔已过多，可减少PLANT，多用REFERENCE/DEEPEN；只有剧情节点成熟时才RESOLVE。\n")
          .append("  - 新埋长期伏笔请提供建议回收窗口（如minVol/maxVol），避免一卷内全收。\n")
          .append("- 揭露(RESOLVE)的硬约束（gating）：\n")
          .append("  1) 必须引用前文已存在的证据锚点(anchors)≥2；\n")
          .append("  2) 锚点时间先于揭露章节；\n")
          .append("  3) 知识边界合法：揭露的信息来源与知情人合理；\n")
          .append("  4) 因果闭环与代价成立（揭露带来明确后果/成本）。\n")
          .append("  若不满足上述条件，则自动降级为DEEPEN，并安排1-2个新的anchors以备后续回收。\n\n");

        sb.append("# 逻辑自洽（章内）\n")
          .append("- 因果闭环：本章关键事件需具备‘触发→行动→结果→后果’，禁止无因果跳跃或‘天降资源’、作者喂饭。\n")
          .append("- 知识边界：角色只能基于其已知信息行动，情报来源可自洽解释；不得预知未来或读者视角。\n")
          .append("- 能力边界：人物能力与限制前后一致；若突破，必须给出铺垫与代价（风险/副作用/牺牲）。\n")
          .append("- 反派不降智：其行动与资源、信息边界相匹配，避免为推动剧情而犯低级错误。\n")
          .append("- 时间承接：承接上一章/上一卷状态，避免状态跳变；必要时用一句话说明状态变化原因。\n")
          .append("- 剧情不平淡：每章必须产生‘推进’（目标/冲突/发现/代价其一），严禁纯过场或流水账。\n\n");

        sb.append("# 反套路与亮点设计\n")
          .append("- 亮点/记忆点：每章至少1个‘记忆点’（高能场面/狠台词/高智博弈/极限选择/价值观冲突）。\n")
          .append("- 非直线推进：避免‘冲突→碾压→结束’的直线流程，提倡多阶段博弈、以退为进、声东击西、误导与反噬。\n")
          .append("- 人设深化：通过行动与选择刷新角色标签，在关键节点呈现【人物高光】。\n")
          .append("- 钩子：章末尽量给出情绪/信息钩子（悬念/危机/选择/反常信号），提升续读欲。\n\n");

        sb.append("# 输出格式（严格JSON数组，不含任何多余文本）\n")
          .append("数组长度必须为").append(count).append("。每个元素是一个对象，字段如下：\n")
          .append("- chapterInVolume: number（1..N）\n")
          .append("- globalChapterNumber: number|null（若已知卷起始章节则给出全局章节号，否则null）\n")
          .append("- direction: string（本章剧情方向，简练有力）\n")
          .append("- keyPlotPoints: string[]（3-6条，使用标签标注关键性：如【亮点】/【人物高光】/【反转】；强调冲突、抉择、代价、后果）\n")
          .append("- emotionalTone: string（如：危机/逆转/悬疑/温情/黑暗/希望/燃）\n")
          .append("- foreshadowAction: string（NONE|PLANT|REFERENCE|DEEPEN|RESOLVE）\n")
          .append("- foreshadowDetail: object|null（{refId?:number, content?:string, targetResolveVolume?:number, resolveWindow?:{min?:number,max?:number}, anchorsUsed?:Array<{vol?:number, ch?:number, hint:string}>, futureAnchorPlan?:string, cost?:string}）\n")
          .append("  - 当 foreshadowAction=RESOLVE 时：必须提供 anchorsUsed，且长度≥2；否则请自动降级为 DEEPEN。\n")
          .append("  - 当 foreshadowAction=PLANT 或 DEEPEN 时：应提供 futureAnchorPlan（简述后续锚点计划）。\n")
          .append("- subplot: string（可选，支线/人设刻画/世界观探索等）\n")
          .append("- antagonism: object（可选，对手/阻力与赌注，如{opponent:string, stakes:string}）\n\n")
          .append("只输出一个纯净的JSON数组，不要markdown，不要代码块，不要解释。\n\n");

        // 为AI计算全局章节号提供提示
        Integer start = volume.getChapterStart();
        if (start != null) {
            sb.append("# 章节编号提示\n")
              .append("- 若给出globalChapterNumber：第一个章节应为").append(start)
              .append("，之后依次+1；否则用null。\n\n");
        }

        sb.append("现在开始生成：请直接输出JSON数组。\n");
        return sb.toString();
    }

    private String buildDecisionLog(Novel novel, NovelVolume volume, NovelOutline outline,
                                    List<NovelForeshadowing> unresolved, String prompt, String raw, int count) {
        StringBuilder sb = new StringBuilder();
        sb.append("[react_decision_log]\n");
        sb.append("route: volume_chapter_outlines_generation\n");
        sb.append("time: ").append(LocalDateTime.now()).append('\n');
        sb.append("msg1: <<<PROMPT>>>\n");
        sb.append(prompt).append('\n');
        sb.append("<<<END_PROMPT>>>\n");
        sb.append("msg2: novelId=").append(novel.getId())
          .append(", title=").append(s(novel.getTitle()))
          .append(", volumeId=").append(volume.getId())
          .append(", volumeNo=").append(nz(volume.getVolumeNumber(), 0))
          .append(", targetCount=").append(count).append('\n');
        sb.append("msg3: volume.contentOutline.len=")
          .append(length(volume.getContentOutline())).append(", outline.len=")
          .append(length(outline.getPlotStructure())).append('\n');
        sb.append("msg4: unresolvedForeshadows.size=")
          .append(unresolved == null ? 0 : unresolved.size()).append('\n');
        sb.append("msg5: <<<RAW_RESPONSE>>>\n").append(limit(raw, 2000)).append('\n');
        sb.append("<<<END_RAW_RESPONSE>>>\n");
        return sb.toString();
    }

    private String extractPureJson(String raw) {
        if (raw == null) throw new RuntimeException("AI返回为空");
        String trimmed = raw.trim();
        // 优先提取```json ... ```
        int fence = indexOfIgnoreCase(trimmed, "```json");
        if (fence != -1) {
            int end = trimmed.indexOf("```", fence + 7);
            if (end != -1) {
                trimmed = trimmed.substring(fence + 7, end).trim();
            } else {
                trimmed = trimmed.substring(fence + 7).trim();
            }
        }
        // 再尝试找到第一个'['到匹配的']'
        int start = trimmed.indexOf('[');
        if (start != -1) {
            int depth = 0; boolean inString = false; char prev = 0;
            for (int i = start; i < trimmed.length(); i++) {
                char c = trimmed.charAt(i);
                if (c == '"' && prev != '\\') inString = !inString;
                if (!inString) {
                    if (c == '[') depth++;
                    else if (c == ']') { depth--; if (depth == 0) { return trimmed.substring(start, i + 1); } }
                }
                prev = c;
            }
        }
        logger.warn("未找到JSON数组，返回原文前800字符");
        return trimmed.substring(0, Math.min(800, trimmed.length()));
    }

    private int indexOfIgnoreCase(String s, String sub) {
        return s.toLowerCase(Locale.ROOT).indexOf(sub.toLowerCase(Locale.ROOT));
    }

    /**
     * 入库：保存章纲 + 伏笔生命周期日志
     * 失败时抛异常，触发事务回滚（旧数据会恢复）
     */
    private void persistOutlines(NovelVolume volume, List<Map<String, Object>> outlines, String reactDecisionLog) {
        if (outlines == null || outlines.isEmpty()) {
            throw new RuntimeException("章纲列表为空，无法入库");
        }

        // 覆盖式写入：先清空该卷旧章纲和伏笔日志，再插入新结果
        // 注意：因为有 @Transactional，如果后续插入失败，删除操作会回滚
        int deletedOutlines = outlineRepo.deleteByVolumeId(volume.getId());
        int deletedLogs = lifecycleLogRepo.deleteByVolumeId(volume.getId());
        logger.info("🧹 已清空旧数据：volumeId={}, 章纲{}条, 伏笔日志{}条",
            volume.getId(), deletedOutlines, deletedLogs);


        int insertedCount = 0;
        for (Map<String, Object> outline : outlines) {
            try {
                VolumeChapterOutline entity = new VolumeChapterOutline();
                entity.setNovelId(volume.getNovelId());
                entity.setVolumeId(volume.getId());
                entity.setVolumeNumber(volume.getVolumeNumber());

                Integer chapterInVolume = getInt(outline, "chapterInVolume");
                Integer globalChapterNumber = getInt(outline, "globalChapterNumber");

                // 验证必填字段
                if (chapterInVolume == null) {
                    logger.error("❌ 章纲缺少必填字段 chapterInVolume: {}", outline);
                    throw new RuntimeException("章纲缺少必填字段 chapterInVolume");
                }

                entity.setChapterInVolume(chapterInVolume);
                entity.setGlobalChapterNumber(globalChapterNumber);
                entity.setDirection(getString(outline, "direction"));
                entity.setKeyPlotPoints(toJson(outline.get("keyPlotPoints")));
                entity.setEmotionalTone(getString(outline, "emotionalTone"));
                entity.setForeshadowAction(getString(outline, "foreshadowAction"));
                entity.setForeshadowDetail(toJson(outline.get("foreshadowDetail")));
                entity.setSubplot(getString(outline, "subplot"));
                entity.setAntagonism(toJson(outline.get("antagonism")));
                entity.setStatus("PENDING");
                entity.setReactDecisionLog(reactDecisionLog);

                outlineRepo.insert(entity);
                insertedCount++;

                logger.debug("✓ 章纲入库成功: 卷内第{}章, 全书第{}章", chapterInVolume, globalChapterNumber);

                // 若有伏笔动作，写入生命周期日志
                String action = entity.getForeshadowAction();
                if (action != null && !action.equals("NONE") && entity.getForeshadowDetail() != null) {
                    try {
                        Map<String, Object> detail = mapper.readValue(entity.getForeshadowDetail(), new TypeReference<Map<String, Object>>(){});
                        Long foreshadowId = getLong(detail, "refId");
                        if (foreshadowId == null && action.equals("PLANT")) {
                            // PLANT 时可能还没有 refId，暂时跳过或创建新伏笔
                            // 这里简化处理：只记录已有 refId 的
                        } else if (foreshadowId != null) {
                            ForeshadowLifecycleLog log = new ForeshadowLifecycleLog();
                            log.setForeshadowId(foreshadowId);
                            log.setNovelId(volume.getNovelId());
                            log.setVolumeId(volume.getId());
                            log.setVolumeNumber(volume.getVolumeNumber());
                            log.setChapterInVolume(entity.getChapterInVolume());
                            log.setGlobalChapterNumber(entity.getGlobalChapterNumber());
                            log.setAction(action);
                            log.setDetail(entity.getForeshadowDetail());
                            lifecycleLogRepo.insert(log);
                        }
                    } catch (Exception e) {
                        logger.warn("⚠️ 解析伏笔详情失败，跳过生命周期日志: {}", e.getMessage());
                    }
                }

            } catch (Exception e) {
                logger.error("❌ 章纲入库失败: chapterInVolume={}, 错误: {}",
                    getInt(outline, "chapterInVolume"), e.getMessage());
                throw new RuntimeException("章纲入库失败（第" + (insertedCount + 1) + "条）: " + e.getMessage(), e);
            }
        }

        logger.info("✅ 成功插入{}条章纲记录", insertedCount);
    }

    private Integer getInt(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return null; }
    }

    private Long getLong(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return null; }
    }

    private String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        if (obj instanceof String) return (String) obj;
        try { return mapper.writeValueAsString(obj); } catch (Exception e) { return null; }
    }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
    private static String s(String v) { return v == null ? "" : v; }
    private static int length(String v) { return v == null ? 0 : v.length(); }
    private static String nz(Object v, Object def) { return String.valueOf(v == null ? def : v); }
    private static String limit(String v, int max) { if (v == null) return ""; return v.length() > max ? v.substring(0, max) + "..." : v; }
}

