package com.novel.controller;

import com.novel.service.CreditRechargeOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth/pay/yipay")
public class YiPayNotifyController {

    private static final Logger logger = LoggerFactory.getLogger(YiPayNotifyController.class);

    @Autowired
    private CreditRechargeOrderService rechargeOrderService;

    @RequestMapping(value = "/notify", method = {RequestMethod.GET, RequestMethod.POST}, produces = "text/plain;charset=UTF-8")
    public String notify(@RequestParam Map<String, String> params) {
        try {
            boolean success = rechargeOrderService.handleYiPayNotify(params);
            return success ? "success" : "fail";
        } catch (Exception ex) {
            logger.error("处理易支付回调失败", ex);
            return "fail";
        }
    }
}
