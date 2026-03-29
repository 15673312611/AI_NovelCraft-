package com.novel.controller;

import com.novel.service.CreditRechargeOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/auth/pay/yipay")
public class YiPayNotifyController {

    private static final Logger logger = LoggerFactory.getLogger(YiPayNotifyController.class);
    private static final Set<String> CALLBACK_PARAM_KEYS = new HashSet<>(Arrays.asList(
            "pid", "trade_no", "out_trade_no", "type", "name", "money",
            "trade_status", "param", "sign", "sign_type"
    ));

    @Autowired
    private CreditRechargeOrderService rechargeOrderService;

    @RequestMapping(value = "/notify", method = {RequestMethod.GET, RequestMethod.POST}, produces = "text/plain;charset=UTF-8")
    public String notify(@RequestParam Map<String, String> params) {
        try {
            boolean success = rechargeOrderService.handleYiPayNotify(extractCallbackParams(params));
            return success ? "success" : "fail";
        } catch (Exception ex) {
            logger.error("处理易支付异步回调失败", ex);
            return "fail";
        }
    }

    @RequestMapping(value = "/return", method = {RequestMethod.GET, RequestMethod.POST})
    public void paymentReturn(@RequestParam Map<String, String> params, HttpServletResponse response) throws IOException {
        boolean success = false;
        try {
            success = rechargeOrderService.handleYiPayNotify(extractCallbackParams(params));
        } catch (Exception ex) {
            logger.error("处理易支付同步回跳失败", ex);
        }

        String redirectBase = params.getOrDefault("redirect", "/settings");
        String orderNo = params.getOrDefault("out_trade_no", "");
        String redirectUrl = appendQuery(redirectBase, "payStatus", success ? "success" : "fail");
        if (!orderNo.isEmpty()) {
            redirectUrl = appendQuery(redirectUrl, "orderNo", orderNo);
        }
        response.sendRedirect(redirectUrl);
    }

    private Map<String, String> extractCallbackParams(Map<String, String> params) {
        Map<String, String> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (CALLBACK_PARAM_KEYS.contains(entry.getKey())) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered;
    }

    private String appendQuery(String url, String key, String value) {
        String separator = url.contains("?") ? "&" : "?";
        return url + separator
                + URLEncoder.encode(key, StandardCharsets.UTF_8)
                + "="
                + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
