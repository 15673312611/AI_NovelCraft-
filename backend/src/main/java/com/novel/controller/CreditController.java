package com.novel.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.novel.common.Result;
import com.novel.common.security.AuthUtils;
import com.novel.domain.entity.AIModel;
import com.novel.domain.entity.CreditTransaction;
import com.novel.domain.entity.User;
import com.novel.domain.entity.UserCredit;
import com.novel.service.CreditService;
import com.novel.service.SystemAIConfigService;
import com.novel.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 灵感点控制器
 */
@RestController
@RequestMapping("/credits")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"}, allowCredentials = "true")
public class CreditController {

    @Autowired
    private CreditService creditService;

    @Autowired
    private SystemAIConfigService systemAIConfigService;

    @Autowired
    private UserService userService;

    /**
     * 获取当前用户灵感点信息
     */
    @GetMapping("/balance")
    public Result<Map<String, Object>> getBalance() {
        Long userId = AuthUtils.getCurrentUserId();
        if (userId == null) {
            return Result.error("请先登录");
        }

        UserCredit credit = creditService.getOrCreateAccount(userId);
        BigDecimal todayConsumption = creditService.getTodayConsumption(userId);
        BigDecimal monthConsumption = creditService.getMonthConsumption(userId);
        BigDecimal warningThreshold = systemAIConfigService.getMinBalanceWarning();

        // 每日免费字数相关
        boolean dailyFreeEnabled = creditService.isDailyFreeCreditsEnabled();
        BigDecimal dailyFreeAmount = creditService.getDailyFreeCreditsAmount();

        Map<String, Object> data = new HashMap<>();
        data.put("balance", credit.getBalance());
        data.put("availableBalance", credit.getAvailableBalance());
        data.put("frozenAmount", credit.getFrozenAmount());
        data.put("totalRecharged", credit.getTotalRecharged());
        data.put("totalConsumed", credit.getTotalConsumed());
        data.put("totalGifted", credit.getTotalGifted());
        data.put("todayConsumption", todayConsumption);
        data.put("monthConsumption", monthConsumption);
        data.put("lowBalance", credit.getBalance().compareTo(warningThreshold) <= 0);
        data.put("warningThreshold", warningThreshold);
        
        // 每日免费字数信息
        data.put("dailyFreeEnabled", dailyFreeEnabled);
        data.put("dailyFreeBalance", credit.getDailyFreeBalance());
        data.put("dailyFreeAmount", dailyFreeAmount);
        data.put("dailyFreeLastReset", credit.getDailyFreeLastReset());
        
        // 总可用余额（每日免费 + 字数包）
        data.put("totalAvailableBalance", credit.getTotalAvailableBalance());

        return Result.success(data);
    }

    /**
     * 获取交易记录
     */
    @GetMapping("/transactions")
    public Result<Map<String, Object>> getTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = AuthUtils.getCurrentUserId();
        if (userId == null) {
            return Result.error("请先登录");
        }

        IPage<CreditTransaction> transactions = creditService.getTransactions(userId, page, size);
        
        // 将modelId转换为displayName，避免暴露真实模型名称
        for (CreditTransaction tx : transactions.getRecords()) {
            if (tx.getModelId() != null && !tx.getModelId().isEmpty()) {
                AIModel model = systemAIConfigService.getModel(tx.getModelId());
                if (model != null && model.getDisplayName() != null) {
                    tx.setModelId(model.getDisplayName());
                } else {
                    tx.setModelId("AI模型"); // 默认显示名称
                }
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("content", transactions.getRecords());
        data.put("totalElements", transactions.getTotal());
        data.put("totalPages", transactions.getPages());
        data.put("currentPage", transactions.getCurrent() - 1);
        data.put("size", transactions.getSize());

        return Result.success(data);
    }

    /**
     * 预估消费
     */
    @PostMapping("/estimate")
    public Result<Map<String, Object>> estimateCost(@RequestBody Map<String, Object> request) {
        String modelId = (String) request.get("modelId");
        String inputText = (String) request.get("inputText");
        Integer estimatedOutputTokens = (Integer) request.getOrDefault("estimatedOutputTokens", 2000);

        if (modelId == null) {
            AIModel defaultModel = systemAIConfigService.getDefaultModel();
            modelId = defaultModel != null ? defaultModel.getModelId() : "deepseek-chat";
        }

        BigDecimal estimatedCost = systemAIConfigService.estimateCost(modelId, inputText != null ? inputText : "", estimatedOutputTokens);
        AIModel model = systemAIConfigService.getModel(modelId);

        Map<String, Object> data = new HashMap<>();
        data.put("estimatedCost", estimatedCost);
        data.put("modelId", modelId);
        data.put("modelName", model != null ? model.getDisplayName() : modelId);
        data.put("inputPricePer1k", model != null ? model.getInputPricePer1k() : BigDecimal.ZERO);
        data.put("outputPricePer1k", model != null ? model.getOutputPricePer1k() : BigDecimal.ZERO);

        return Result.success(data);
    }

    /**
     * 检查余额是否足够
     */
    @PostMapping("/check")
    public Result<Map<String, Object>> checkBalance(@RequestBody Map<String, Object> request) {
        Long userId = AuthUtils.getCurrentUserId();
        if (userId == null) {
            return Result.error("请先登录");
        }

        BigDecimal requiredAmount = new BigDecimal(request.get("amount").toString());
        BigDecimal availableBalance = creditService.getAvailableBalance(userId);
        boolean sufficient = availableBalance.compareTo(requiredAmount) >= 0;

        Map<String, Object> data = new HashMap<>();
        data.put("sufficient", sufficient);
        data.put("availableBalance", availableBalance);
        data.put("requiredAmount", requiredAmount);
        data.put("shortfall", sufficient ? BigDecimal.ZERO : requiredAmount.subtract(availableBalance));

        return Result.success(data);
    }

    /**
     * 获取可用模型列表
     */
    @GetMapping("/models")
    public Result<List<AIModel>> getAvailableModels() {
        List<AIModel> models = systemAIConfigService.getAvailableModels();
        return Result.success(models);
    }

    /**
     * 获取默认模型
     */
    @GetMapping("/models/default")
    public Result<AIModel> getDefaultModel() {
        AIModel model = systemAIConfigService.getDefaultModel();
        if (model == null) {
            return Result.error("未配置默认模型");
        }
        return Result.success(model);
    }
}
