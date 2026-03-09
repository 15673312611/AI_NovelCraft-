package com.novel.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.novel.admin.entity.CreditPackage;
import com.novel.admin.entity.CreditTransaction;
import com.novel.admin.entity.UserCredit;
import com.novel.admin.service.AdminCreditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端字数点控制器
 */
@RestController
@RequestMapping("/credits")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AdminCreditController {

    private final AdminCreditService creditService;

    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> getUserCredits(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {

        IPage<UserCredit> result = creditService.getUserCredits(page, size, keyword);

        Map<String, Object> response = new HashMap<>();
        response.put("content", result.getRecords());
        response.put("totalElements", result.getTotal());
        response.put("totalPages", result.getPages());
        response.put("currentPage", result.getCurrent() - 1);
        response.put("size", result.getSize());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<UserCredit> getUserCredit(@PathVariable Long userId) {
        UserCredit credit = creditService.getUserCredit(userId);
        if (credit == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(credit);
    }

    @PostMapping("/users/{userId}/recharge")
    public ResponseEntity<Map<String, Object>> recharge(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> request) {

        BigDecimal amount = new BigDecimal(request.get("amount").toString());
        String description = (String) request.get("description");
        Long operatorId = request.get("operatorId") != null
                ? Long.valueOf(request.get("operatorId").toString())
                : null;

        boolean success = creditService.recharge(userId, amount, description, operatorId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", success ? "充值成功" : "充值失败");

        return ResponseEntity.ok(response);
    }

    @PostMapping("/users/{userId}/gift")
    public ResponseEntity<Map<String, Object>> gift(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> request) {

        BigDecimal amount = new BigDecimal(request.get("amount").toString());
        String description = (String) request.get("description");
        Long operatorId = request.get("operatorId") != null
                ? Long.valueOf(request.get("operatorId").toString())
                : null;

        boolean success = creditService.gift(userId, amount, description, operatorId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", success ? "赠送成功" : "赠送失败");

        return ResponseEntity.ok(response);
    }

    @PostMapping("/users/{userId}/adjust")
    public ResponseEntity<Map<String, Object>> adjustBalance(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> request) {

        BigDecimal amount = new BigDecimal(request.get("amount").toString());
        String description = (String) request.get("description");
        Long operatorId = request.get("operatorId") != null
                ? Long.valueOf(request.get("operatorId").toString())
                : null;

        boolean success = creditService.adjustBalance(userId, amount, description, operatorId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", success ? "调整成功" : "调整失败");

        return ResponseEntity.ok(response);
    }

    @GetMapping("/transactions")
    public ResponseEntity<Map<String, Object>> getTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String type) {

        IPage<CreditTransaction> result = creditService.getTransactions(page, size, userId, type);

        Map<String, Object> response = new HashMap<>();
        response.put("content", result.getRecords());
        response.put("totalElements", result.getTotal());
        response.put("totalPages", result.getPages());
        response.put("currentPage", result.getCurrent() - 1);
        response.put("size", result.getSize());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        return ResponseEntity.ok(creditService.getStatistics());
    }

    @GetMapping("/model-usage")
    public ResponseEntity<List<Map<String, Object>>> getModelUsageStats(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(creditService.getModelUsageStats(days));
    }

    @GetMapping("/packages")
    public ResponseEntity<List<CreditPackage>> getAllPackages() {
        return ResponseEntity.ok(creditService.getAllPackages());
    }

    @PostMapping("/packages")
    public ResponseEntity<CreditPackage> createPackage(@RequestBody CreditPackage pkg) {
        return ResponseEntity.ok(creditService.createPackage(pkg));
    }

    @PutMapping("/packages/{id}")
    public ResponseEntity<CreditPackage> updatePackage(
            @PathVariable Long id,
            @RequestBody CreditPackage pkg) {
        return ResponseEntity.ok(creditService.updatePackage(id, pkg));
    }

    @DeleteMapping("/packages/{id}")
    public ResponseEntity<Map<String, Object>> deletePackage(@PathVariable Long id) {
        boolean success = creditService.deletePackage(id);
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/config/registration-bonus")
    public ResponseEntity<String> getRegistrationBonus() {
        return ResponseEntity.ok(creditService.getRegistrationBonus());
    }

    @PutMapping("/config/registration-bonus")
    public ResponseEntity<Map<String, Object>> updateRegistrationBonus(
            @RequestBody Map<String, String> body) {
        String amount = body.get("amount");
        if (amount != null) {
            creditService.updateRegistrationBonus(amount);
        }
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/config/payment")
    public ResponseEntity<Map<String, Object>> getPaymentConfig() {
        return ResponseEntity.ok(creditService.getPaymentConfig());
    }

    @PutMapping("/config/payment")
    public ResponseEntity<Map<String, Object>> updatePaymentConfig(@RequestBody Map<String, Object> body) {
        creditService.updatePaymentConfig(body);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/config/payment/verify")
    public ResponseEntity<Map<String, Object>> verifyPaymentConfig(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(creditService.verifyPaymentConfig(body));
    }
}
