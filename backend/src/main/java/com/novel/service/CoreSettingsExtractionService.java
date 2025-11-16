package com.novel.service;

import com.novel.domain.entity.Novel;
import com.novel.domain.entity.NovelOutline;
import com.novel.repository.NovelOutlineRepository;
import com.novel.repository.NovelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 核心设定提炼服务
 *
 * 功能：从完整大纲中提炼核心设定信息，用于后续章节写作时的上下文参考
 * 目的：避免AI产生"上帝视角"，防止剧透后续剧情
 */
@Service
public class CoreSettingsExtractionService {
    
    private static final Logger logger = LoggerFactory.getLogger(CoreSettingsExtractionService.class);
    
    @Autowired
    private NovelOutlineRepository outlineRepository;

    @Autowired
    private NovelRepository novelRepository;

    @Autowired
    private AIWritingService aiWritingService;
    
    /**
     * 异步提炼核心设定
     * 在确认大纲后调用，不阻塞用户操作
     */
    @Async
    @Transactional
    public void extractCoreSettingsAsync(Long outlineId, com.novel.dto.AIConfigRequest aiConfig) {
        logger.info("🔍 开始异步提炼大纲核心设定: outlineId={}", outlineId);
        
        try {
            NovelOutline outline = outlineRepository.selectById(outlineId);
            if (outline == null) {
                logger.error("❌ 大纲不存在: {}", outlineId);
                return;
            }
            
            String plotStructure = outline.getPlotStructure();
            if (plotStructure == null || plotStructure.trim().isEmpty()) {
                logger.warn("⚠️ 大纲内容为空，跳过核心设定提炼: outlineId={}", outlineId);
                return;
            }

            // 构建提炼提示词（包含用户原始构思 + 完整大纲；标题来自 Novel 表）
            String basicIdea = outline.getBasicIdea();
            Long novelId = outline.getNovelId();
            String extractionPrompt = buildExtractionPrompt(
                novelId,
                basicIdea,
                plotStructure
            );

            // 调用AI提炼核心设定
            logger.info("📝 调用AI提炼核心设定，大纲长度: {} 字", plotStructure.length());
            String coreSettings = aiWritingService.generateContent(
                extractionPrompt, 
                "core_settings_extraction", 
                aiConfig
            );
            
            if (coreSettings == null || coreSettings.trim().isEmpty()) {
                logger.error("❌ AI返回的核心设定为空");
                return;
            }
            
            // 保存核心设定到数据库
            outline.setCoreSettings(coreSettings);
            outlineRepository.updateById(outline);
            
            logger.info("✅ 核心设定提炼完成: outlineId={}, 设定长度={} 字", 
                outlineId, coreSettings.length());
            
        } catch (Exception e) {
            logger.error("❌ 提炼核心设定失败: outlineId={}", outlineId, e);
        }
    }
    
    /**
     * 构建核心设定提炼提示词
     */
    private String buildExtractionPrompt(Long novelId, String basicIdea, String plotStructure) {
        StringBuilder prompt = new StringBuilder();

        // 角色与任务目标
        prompt.append("角色：资深网文编辑与设定统筹\n\n");
        prompt.append("任务目标：\n");
        prompt.append("从完整大纲中提炼仅包含核心世界观、关键设定与风格锚点的蓝图。只描述“是什么”，不描述“发生了什么”。严禁上帝视角、禁止剧情化表达。缺项不自填，信息不足时仅提出澄清问题并停止。\n\n");

        // 输入字段
        prompt.append("输入字段：\n\n");
        String title = "";
        try {
            if (novelId != null) {
                Novel novel = novelRepository.selectById(novelId);
                if (novel != null && novel.getTitle() != null && !novel.getTitle().trim().isEmpty()) {
                    title = novel.getTitle().trim();
                }
            }
        } catch (Exception ignore) {}
        if (!title.isEmpty()) {
            prompt.append("作品名：").append(title).append("\n");
        }
        StringBuilder source = new StringBuilder();
        if (plotStructure != null && !plotStructure.trim().isEmpty()) {
            source.append("【完整大纲】\n").append(plotStructure.trim()).append("\n\n");
        }
        if (basicIdea != null && !basicIdea.trim().isEmpty()) {
            source.append("【原始设定】\n").append(basicIdea.trim()).append("\n");
        }
        prompt.append("完整大纲与原始设定：\n").append(source.toString()).append("\n");

        // 输出结构
        prompt.append("输出结构（编号，纯文本）：\n\n");
        prompt.append("1. 作品定位\n");
        prompt.append("类型标签：不超过3个\n");
        prompt.append("基调：不超过2个词组\n");
        prompt.append("一句话核心概念：1句，抽象设定，不含事件词\n");
        prompt.append("主题命题：2-4个词，如权力、自由、记忆、信仰\n");
        prompt.append("受众期待：2个词组，如快节奏爽感、设定推理\n\n");

        prompt.append("2. 世界观规则\n");
        prompt.append("时代与技术层级：静态描述\n");
        prompt.append("核心法则：1-3条可检验规则\n");
        prompt.append("空间与权力结构：区域划分与权力分布\n");
        prompt.append("社会与文化规范：主流秩序与价值观\n\n");

        prompt.append("3. 力量体系\n");
        prompt.append("力量来源：列表\n");
        prompt.append("等级或分类：名称与划分标准\n");
        prompt.append("使用代价与限制：硬约束\n");
        prompt.append("相克与边界：简明规则\n\n");

        prompt.append("4. 阵营与组织\n");
        prompt.append("每个阵营包含：宗旨或立场、资源或优势、影响范围\n");
        prompt.append("静态设定，不写互动与走向\n\n");

        prompt.append("5. 关键名词表\n");
        prompt.append("术语、物品、地点、技术各3-8条\n");
        prompt.append("每条≤20字，功能性定义\n");
        prompt.append("无资料则标注：待定\n\n");

        prompt.append("6. 主角起点设定（非剧情）\n");
        prompt.append("身份与出身：初始状态\n");
        prompt.append("固有能力与短板：客观特征\n");
        prompt.append("长期动机（抽象）：如自由、秩序、探索\n");
        prompt.append("行事风格：2-3个标签\n");
        prompt.append("价值观标签：2-3个词\n\n");

        prompt.append("7. 核心冲突类型（抽象）\n");
        prompt.append("矛盾范式：如个体与秩序、传统与变革、理性与信仰\n");
        prompt.append("不涉及事件与时间线\n\n");

        prompt.append("8. 写作边界与风格提示\n");
        prompt.append("必须保持：本书独特性要素\n");
        prompt.append("禁忌清单：不可触碰的内容与写法\n");
        prompt.append("语言与节奏：风格关键词与描写侧重点\n");
        prompt.append("描写优先级：如对话>战斗>环境\n\n");

        prompt.append("9. 一致性锚点\n");
        prompt.append("术语锁定：统一用词与拼写\n");
        prompt.append("风格锁定：2-4个稳定风格标签\n\n");

        prompt.append("10. 有意留白\n");
        prompt.append("列出刻意不设定的领域，避免AI自作主张填坑\n\n");

        prompt.append("11. 澄清问题（必要时）\n");
        prompt.append("若输入不足，提出最多5个定义型问题；否则省略\n\n");

        // 兼容与缺项规则
        prompt.append("兼容与缺项规则：\n\n");
        prompt.append("条目数量为上限与建议范围；允许为空或“待定”\n");
        prompt.append("信息缺失不补写；不得生成新核心设定填空\n");
        prompt.append("若术语冲突，选定一个主用词，其余标记为别名\n");
        prompt.append("若整体信息不足，仅输出第11项澄清问题并停止\n\n");

        // 硬性限制
        prompt.append("硬性限制（必须遵守）：\n\n");
        prompt.append("禁止任何剧情、事件、遭遇、任务、冲突展开、时间线与转折描述\n");
        prompt.append("禁止使用或暗示未来式与走向判断；禁用词示例：将会、后来、最终、结局、命运、注定、反转、遇到、发现（作事件用时）、第X章、剧情、伏笔\n");
        prompt.append("禁止上帝视角与全局掌控式结论；不得评价发展趋势\n");
        prompt.append("禁止角色心理变化过程与关系演化；仅允许稳定人格标签\n");
        prompt.append("禁止创设输入以外的新核心名词与规则；缺项标注“待定”\n");
        prompt.append("用定义句与名词句呈现；避免因果链与推演\n\n");

        // 流程要求
        prompt.append("流程要求：\n\n");
        prompt.append("读取输入并判断信息充足性\n");
        prompt.append("若不足，仅输出第11项澄清问题并停止\n");
        prompt.append("若充足，按上述结构生成设定\n");
        prompt.append("生成后自检以下清单，不合规则重写：是否出现事件或走向；是否新增核心设定；是否含上帝视角；是否未用短句定义\n\n");

        // 输出格式
        prompt.append("输出格式：\n\n");
        prompt.append("纯文本，编号与短列表\n");
        prompt.append("不使用Markdown、不加引号、不写额外解释\n");

        return prompt.toString();
    }



    
    /**
     * 同步提炼核心设定（用于测试或特殊场景）
     */
    @Transactional
    public String extractCoreSettingsSync(Long outlineId, com.novel.dto.AIConfigRequest aiConfig) {
        logger.info("🔍 开始同步提炼大纲核心设定: outlineId={}", outlineId);
        
        NovelOutline outline = outlineRepository.selectById(outlineId);
        if (outline == null) {
            throw new RuntimeException("大纲不存在: " + outlineId);
        }
        
        String plotStructure = outline.getPlotStructure();
        if (plotStructure == null || plotStructure.trim().isEmpty()) {
            throw new RuntimeException("大纲内容为空");
        }

        String basicIdea = outline.getBasicIdea();
        Long novelId = outline.getNovelId();
        String extractionPrompt = buildExtractionPrompt(
            novelId,
            basicIdea,
            plotStructure
        );
        String coreSettings = aiWritingService.generateContent(
            extractionPrompt, 
            "core_settings_extraction", 
            aiConfig
        );
        
        if (coreSettings == null || coreSettings.trim().isEmpty()) {
            throw new RuntimeException("AI返回的核心设定为空");
        }
        
        outline.setCoreSettings(coreSettings);
        outlineRepository.updateById(outline);
        
        logger.info("✅ 核心设定提炼完成: outlineId={}, 设定长度={} 字", 
            outlineId, coreSettings.length());
        
        return coreSettings;
    }
    
    /**
     * 获取核心设定（如果不存在则返回null）
     */
    public String getCoreSettings(Long outlineId) {
        NovelOutline outline = outlineRepository.selectById(outlineId);
        if (outline == null) {
            return null;
        }
        return outline.getCoreSettings();
    }
}

