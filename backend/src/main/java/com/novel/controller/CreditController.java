package com.novel.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.novel.common.Result;
import com.novel.common.security.AuthUtils;
import com.novel.domain.entity.AIModel;
import com.novel.domain.entity.CreditTransaction;
import com.novel.domain.entity.UserCredit;
import com.novel.dto.PublicAIModelDto;
import com.novel.dto.RedeemCodeRequest;
import com.novel.service.CreditRedeemCodeService;
import com.novel.service.CreditService;
import com.novel.service.SystemAIConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/credits")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"}, allowCredentials = "true")
public class CreditController {

    @Autowired
    private CreditService creditService;

    @Autowired
    private CreditRedeemCodeService creditRedeemCodeService;

    @Autowired
    private SystemAIConfigService systemAIConfigService;

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
        data.put("dailyFreeEnabled", dailyFreeEnabled);
        data.put("dailyFreeBalance", credit.getDailyFreeBalance());
        data.put("dailyFreeAmount", dailyFreeAmount);
        data.put("dailyFreeLastReset", credit.getDailyFreeLastReset());
        data.put("totalAvailableBalance", credit.getTotalAvailableBalance());

        return Result.success(data);
    }

    @GetMapping("/transactions")
    public Result<Map<String, Object>> getTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = AuthUtils.getCurrentUserId();
        if (userId == null) {
            return Result.error("请先登录");
        }

        IPage<CreditTransaction> transactions = creditService.getTransactions(userId, page, size);

        for (CreditTransaction tx : transactions.getRecords()) {
            if (tx.getModelId() != null && !tx.getModelId().isEmpty()) {
                AIModel model = systemAIConfigService.getModel(tx.getModelId());
                if (model != null && model.getDisplayName() != null) {
                    tx.setModelId(model.getDisplayName());
                } else {
                    tx.setModelId("AI模型");
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

    @PostMapping("/redeem")
    public Result<Map<String, Object>> redeem(@RequestBody RedeemCodeRequest request) {
        Long userId = AuthUtils.getCurrentUserId();
        if (userId == null) {
            return Result.error("请先登录");
        }

        return Result.success(creditRedeemCodeService.redeem(userId, request.getCode()));
    }

    @GetMapping("/models")
    public Result<List<PublicAIModelDto>> getAvailableModels() {
        return Result.success(
                systemAIConfigService.getAvailableModels().stream()
                        .map(PublicAIModelDto::from)
                        .collect(Collectors.toList())
        );
    }
}
