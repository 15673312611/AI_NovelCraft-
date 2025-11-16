package com.novel.agentic.service.diagnostics;

import com.novel.agentic.service.graph.EntityExtractionRetryService;
import com.novel.agentic.service.performance.GraphQueryCache;
import com.novel.repository.NovelRepository;
import com.novel.domain.entity.Novel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 长篇小说生成诊断服务
 * 
 * 用途：检测可能影响长篇小说生成的问题
 */
@Service
public class LongNovelDiagnosticsService {
    
    private static final Logger logger = LoggerFactory.getLogger(LongNovelDiagnosticsService.class);
    
    @Autowired
    private NovelRepository novelRepository;
    
    @Autowired(required = false)
    private EntityExtractionRetryService retryService;
    
    @Autowired(required = false)
    private GraphQueryCache queryCache;
    
    /**
     * 全面诊断小说生成系统
     */
    public Map<String, Object> diagnose(Long novelId) {
        logger.info("🔍 开始诊断小说生成系统: novelId={}", novelId);
        
        Map<String, Object> report = new HashMap<>();
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        
        // 1. 检查小说基础信息
        Novel novel = novelRepository.selectById(novelId);
        if (novel == null) {
            errors.add("小说不存在: " + novelId);
            report.put("errors", errors);
            return report;
        }
        
        report.put("novelTitle", novel.getTitle());
        // genre intentionally omitted from diagnostics; not user-provided anymore
        
        // 2. 检查大纲
        if (novel.getOutline() == null || novel.getOutline().isEmpty()) {
            warnings.add("缺少小说大纲，可能导致生成内容偏离预期");
            suggestions.add("建议先生成完整的小说大纲");
        } else {
            int outlineLength = novel.getOutline().length();
            report.put("outlineLength", outlineLength);
            
            if (outlineLength < 500) {
                warnings.add("大纲过短（" + outlineLength + "字），可能不够详细");
                suggestions.add("建议补充大纲内容，至少1000字以上");
            } else if (outlineLength > 10000) {
                warnings.add("大纲过长（" + outlineLength + "字），可能超出Token预算");
                suggestions.add("建议精简大纲，保留核心内容");
            }
        }
        
        // 3. 检查卷蓝图
        // TODO: 查询卷数量
        report.put("volumeCount", "待实现");
        
        // 4. 检查实体抽取失败情况
        if (retryService != null) {
            List<Map<String, Object>> failedExtractions = retryService.getFailedExtractions();
            report.put("failedExtractionCount", failedExtractions.size());
            
            if (!failedExtractions.isEmpty()) {
                errors.add("有" + failedExtractions.size() + "个章节实体抽取失败");
                report.put("failedExtractions", failedExtractions);
                suggestions.add("运行 /api/agentic/graph/retry-failed 重试失败的抽取");
            }
        }
        
        // 5. 检查缓存情况
        if (queryCache != null) {
            Map<String, Object> cacheStats = queryCache.getStats();
            report.put("cacheStats", cacheStats);
        }
        
        // 6. 长篇小说特殊检查
        // 假设章节数 = 当前已生成章节数
        // TODO: 查询实际章节数
        Integer estimatedChapters = 100; // 示例
        
        if (estimatedChapters > 50) {
            warnings.add("检测到长篇小说（预计" + estimatedChapters + "章）");
            suggestions.add("建议使用代理式写作系统（启用图谱+ReAct）");
            suggestions.add("建议每生成10-20章后，进行一次一致性检查");
            
            // Token成本预估
            int estimatedTokens = estimatedChapters * 18000; // 每章平均18k tokens
            double estimatedCost = estimatedTokens / 1000.0 * 0.001; // 假设$0.001/1k tokens
            
            report.put("estimatedTotalTokens", estimatedTokens);
            report.put("estimatedCostUSD", String.format("$%.2f", estimatedCost));
            
            if (estimatedCost > 10) {
                warnings.add("预计Token成本较高：$" + String.format("%.2f", estimatedCost));
                suggestions.add("建议启用Token预算控制（已内置）");
                suggestions.add("建议分批生成，避免一次性消耗过多");
            }
        }
        
        // 7. 生成诊断总结
        report.put("warnings", warnings);
        report.put("errors", errors);
        report.put("suggestions", suggestions);
        
        String healthStatus;
        if (!errors.isEmpty()) {
            healthStatus = "ERROR";
        } else if (warnings.size() > 3) {
            healthStatus = "WARNING";
        } else {
            healthStatus = "HEALTHY";
        }
        
        report.put("healthStatus", healthStatus);
        report.put("timestamp", new Date());
        
        logger.info("✅ 诊断完成: 状态={}, 警告数={}, 错误数={}", 
            healthStatus, warnings.size(), errors.size());
        
        return report;
    }
    
    /**
     * 生成长篇小说最佳实践建议
     */
    public Map<String, Object> getBestPractices() {
        Map<String, Object> practices = new HashMap<>();
        
        List<String> beforeWriting = Arrays.asList(
            "1. 创建详细大纲（1000-3000字），明确主线和支线",
            "2. 设定完整的世界观规则（力量体系、社会结构等）",
            "3. 详细设定主要角色（性格、目标、成长路径）",
            "4. 划分卷蓝图，每卷50-100章，明确阶段目标",
            "5. 准备关键情节点列表（重要转折、高潮点）"
        );
        
        List<String> duringWriting = Arrays.asList(
            "1. 每生成5-10章检查一次情节连贯性",
            "2. 注意观察Token消耗和成本",
            "3. 定期检查实体抽取是否成功（图谱数据是否完整）",
            "4. 对于重要章节，生成后人工审核并调整",
            "5. 使用伏笔管理功能，避免遗忘未回收的伏笔"
        );
        
        List<String> troubleshooting = Arrays.asList(
            "问题1：AI生成内容跑题 → 检查大纲是否清晰，补充卷蓝图",
            "问题2：角色性格不一致 → 检查角色设定，添加关键性格特征到大纲",
            "问题3：情节重复 → 查看图谱历史事件，AI会自动避免",
            "问题4：伏笔未回收 → 查看未回收伏笔列表，手动指定回收时机",
            "问题5：Token成本过高 → 启用Token预算控制，精简大纲和卷蓝图"
        );
        
        practices.put("beforeWriting", beforeWriting);
        practices.put("duringWriting", duringWriting);
        practices.put("troubleshooting", troubleshooting);
        
        return practices;
    }
}


