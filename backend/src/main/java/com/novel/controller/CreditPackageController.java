package com.novel.controller;

import com.novel.common.Result;
import com.novel.common.security.AuthUtils;
import com.novel.domain.entity.CreditPackage;
import com.novel.service.CreditPackageService;
import com.novel.service.SystemAIConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 充值套餐控制器
 */
@RestController
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"}, allowCredentials = "true")
public class CreditPackageController {

    @Autowired
    private CreditPackageService creditPackageService;

    @Autowired
    private SystemAIConfigService configService;

    // --- 公开接口 ---

    /**
     * 获取所有启用套餐（用户端）
     */
    @GetMapping("/credits/packages")
    public Result<List<CreditPackage>> getActivePackages() {
        return Result.success(creditPackageService.getActivePackages());
    }

    // --- 管理后台接口 ---

    /**
     * 获取所有套餐（管理端）
     */
    @GetMapping("/admin/credits/packages")
    public Result<List<CreditPackage>> getAllPackages() {
        // TODO: check admin permission
        return Result.success(creditPackageService.getAllPackages());
    }

    /**
     * 创建套餐
     */
    @PostMapping("/admin/credits/packages")
    public Result<CreditPackage> createPackage(@RequestBody CreditPackage pkg) {
        creditPackageService.save(pkg);
        return Result.success(pkg);
    }

    /**
     * 更新套餐
     */
    @PutMapping("/admin/credits/packages/{id}")
    public Result<CreditPackage> updatePackage(@PathVariable Long id, @RequestBody CreditPackage pkg) {
        pkg.setId(id);
        creditPackageService.updateById(pkg);
        return Result.success(pkg);
    }

    /**
     * 删除套餐
     */
    @DeleteMapping("/admin/credits/packages/{id}")
    public Result<Void> deletePackage(@PathVariable Long id) {
        creditPackageService.removeById(id);
        return Result.success();
    }

    // --- 注册赠送配置 ---

    /**
     * 获取注册赠送字数配置
     */
    @GetMapping("/admin/credits/config/registration-bonus")
    public Result<String> getRegistrationBonus() {
        String value = configService.getConfig("new_user_gift_credits");
        return Result.success(value != null ? value : "0");
    }

    /**
     * 更新注册赠送字数配置
     */
    @PutMapping("/admin/credits/config/registration-bonus")
    public Result<Void> updateRegistrationBonus(@RequestBody Map<String, String> body) {
        String amount = body.get("amount");
        if (amount != null) {
            configService.updateConfig("new_user_gift_credits", amount, "新用户注册赠送灵感点");
        }
        return Result.success();
    }
}