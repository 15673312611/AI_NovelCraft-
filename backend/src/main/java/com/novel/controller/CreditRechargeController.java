package com.novel.controller;

import com.novel.common.Result;
import com.novel.common.security.AuthUtils;
import com.novel.service.CreditRechargeOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/credits/recharge")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"}, allowCredentials = "true")
public class CreditRechargeController {

    @Autowired
    private CreditRechargeOrderService rechargeOrderService;

    @PostMapping("/orders")
    public Result<Map<String, Object>> createOrder(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long userId = AuthUtils.getCurrentUserId();
        if (userId == null) {
            return Result.error("请先登录");
        }
        Object packageIdValue = body.get("packageId");
        if (packageIdValue == null) {
            return Result.error("请选择充值套餐");
        }
        Long packageId = Long.valueOf(String.valueOf(packageIdValue));
        String payType = String.valueOf(body.getOrDefault("payType", "alipay"));
        Map<String, Object> data = rechargeOrderService.createOrder(userId, packageId, payType, request);
        return Result.success(data);
    }

    @GetMapping("/orders/{orderNo}")
    public Result<Map<String, Object>> getOrder(@PathVariable String orderNo) {
        Long userId = AuthUtils.getCurrentUserId();
        if (userId == null) {
            return Result.error("请先登录");
        }
        Map<String, Object> data = rechargeOrderService.getOrderForUser(userId, orderNo);
        return Result.success(data);
    }
}
