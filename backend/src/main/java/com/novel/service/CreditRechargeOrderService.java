package com.novel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.domain.entity.CreditPackage;
import com.novel.domain.entity.CreditRechargeOrder;
import com.novel.repository.CreditRechargeOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class CreditRechargeOrderService {

    private static final Logger logger = LoggerFactory.getLogger(CreditRechargeOrderService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter ORDER_NO_TIME_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private static final String PROVIDER_YIPAY = "YIPAY";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PAID = "PAID";
    private static final String STATUS_CLOSED = "CLOSED";

    @Autowired
    private CreditPackageService creditPackageService;

    @Autowired
    private CreditRechargeOrderRepository orderRepository;

    @Autowired
    private CreditService creditService;

    @Autowired
    private SystemAIConfigService configService;

    public Map<String, Object> createOrder(Long userId, Long packageId, String payType, HttpServletRequest request) {
        CreditPackage creditPackage = creditPackageService.getById(packageId);
        if (creditPackage == null || !Boolean.TRUE.equals(creditPackage.getIsActive())) {
            throw new RuntimeException("充值套餐不可用");
        }

        String normalizedPayType = normalizePayType(payType);
        if (normalizedPayType == null) {
            throw new RuntimeException("支付方式不支持");
        }

        YiPayConfig yiPayConfig = loadYiPayConfig();
        if (!yiPayConfig.enabled) {
            throw new RuntimeException("充值功能暂未开放");
        }
        validateYiPayConfig(yiPayConfig);

        String orderNo = generateOrderNo();
        int expireMinutes = yiPayConfig.orderExpireMinutes;
        String baseUrl = buildBaseUrl(request);
        String notifyUrl = hasText(yiPayConfig.notifyUrl)
                ? yiPayConfig.notifyUrl
                : baseUrl + "/auth/pay/yipay/notify";
        String returnUrl = hasText(yiPayConfig.returnUrl)
                ? yiPayConfig.returnUrl
                : buildDefaultReturnUrl(request, baseUrl);

        BigDecimal amount = creditPackage.getPrice().setScale(2, RoundingMode.HALF_UP);
        String payUrl = buildYiPayUrl(yiPayConfig, orderNo, normalizedPayType, amount, creditPackage.getName(), notifyUrl, returnUrl, userId);

        CreditRechargeOrder order = new CreditRechargeOrder();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setPackageId(creditPackage.getId());
        order.setPackageName(creditPackage.getName());
        order.setPackagePrice(amount);
        order.setPackageCredits(creditPackage.getCredits());
        order.setPaymentProvider(PROVIDER_YIPAY);
        order.setPaymentType(normalizedPayType);
        order.setStatus(STATUS_PENDING);
        order.setPaymentUrl(payUrl);
        order.setClientIp(resolveClientIp(request));
        order.setExpiredAt(LocalDateTime.now().plusMinutes(expireMinutes));
        orderRepository.insert(order);

        return buildOrderResponse(order);
    }

    public Map<String, Object> getOrderForUser(Long userId, String orderNo) {
        CreditRechargeOrder order = orderRepository.findByOrderNoAndUserId(orderNo, userId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        closeIfExpired(order);
        CreditRechargeOrder latest = orderRepository.findByOrderNo(orderNo);
        return buildOrderResponse(latest == null ? order : latest);
    }

    @Transactional
    public boolean handleYiPayNotify(Map<String, String> params) {
        String orderNo = trimToEmpty(params.get("out_trade_no"));
        String sign = trimToEmpty(params.get("sign"));
        String tradeStatus = trimToEmpty(params.get("trade_status"));

        if (!hasText(orderNo) || !hasText(sign) || !hasText(tradeStatus)) {
            logger.warn("易支付回调参数缺失: {}", params.keySet());
            return false;
        }

        if (!"TRADE_SUCCESS".equalsIgnoreCase(tradeStatus) && !"TRADE_FINISHED".equalsIgnoreCase(tradeStatus)) {
            logger.warn("易支付交易状态非成功, orderNo={}, status={}", orderNo, tradeStatus);
            return false;
        }

        YiPayConfig yiPayConfig = loadYiPayConfig();
        validateYiPayConfig(yiPayConfig);

        if (hasText(params.get("pid")) && !yiPayConfig.pid.equals(trimToEmpty(params.get("pid")))) {
            logger.warn("易支付pid不匹配, orderNo={}, pid={}", orderNo, params.get("pid"));
            return false;
        }

        if (!verifySign(params, yiPayConfig.key)) {
            logger.warn("易支付回调签名校验失败, orderNo={}", orderNo);
            return false;
        }

        CreditRechargeOrder order = orderRepository.findByOrderNo(orderNo);
        if (order == null) {
            logger.warn("充值订单不存在, orderNo={}", orderNo);
            return false;
        }

        if (STATUS_PAID.equals(order.getStatus())) {
            return true;
        }

        if (!STATUS_PENDING.equals(order.getStatus())) {
            logger.warn("订单状态不可支付, orderNo={}, status={}", orderNo, order.getStatus());
            return false;
        }

        String money = trimToEmpty(params.get("money"));
        if (!hasText(money)) {
            logger.warn("易支付回调金额缺失, orderNo={}", orderNo);
            return false;
        }
        BigDecimal callbackAmount;
        try {
            callbackAmount = new BigDecimal(money).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            logger.warn("易支付回调金额格式非法, orderNo={}, money={}", orderNo, money);
            return false;
        }
        BigDecimal orderAmount = order.getPackagePrice() == null
                ? BigDecimal.ZERO
                : order.getPackagePrice().setScale(2, RoundingMode.HALF_UP);
        if (callbackAmount.compareTo(orderAmount) != 0) {
            logger.warn("易支付回调金额不匹配, orderNo={}, callback={}, order={}", orderNo, callbackAmount, orderAmount);
            return false;
        }

        String tradeNo = trimToEmpty(params.get("trade_no"));
        String notifyRaw = toJson(params);
        int updatedRows = orderRepository.markPaidIfPending(orderNo, tradeNo, notifyRaw);
        if (updatedRows <= 0) {
            CreditRechargeOrder latest = orderRepository.findByOrderNo(orderNo);
            return latest != null && STATUS_PAID.equals(latest.getStatus());
        }

        BigDecimal credits = BigDecimal.valueOf(order.getPackageCredits() == null ? 0L : order.getPackageCredits());
        String description = String.format("在线充值字数包[%s] 订单:%s", order.getPackageName(), orderNo);
        boolean recharged = creditService.recharge(order.getUserId(), credits, description, null);
        if (!recharged) {
            throw new RuntimeException("充值到账失败，请重试");
        }
        return true;
    }

    private Map<String, Object> buildOrderResponse(CreditRechargeOrder order) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderNo", order.getOrderNo());
        data.put("packageId", order.getPackageId());
        data.put("packageName", order.getPackageName());
        data.put("packagePrice", order.getPackagePrice());
        data.put("packageCredits", order.getPackageCredits());
        data.put("paymentType", order.getPaymentType());
        data.put("status", order.getStatus());
        data.put("payUrl", order.getPaymentUrl());
        data.put("paidAt", order.getPaidAt());
        data.put("expiredAt", order.getExpiredAt());
        data.put("createdAt", order.getCreatedAt());
        return data;
    }

    private void closeIfExpired(CreditRechargeOrder order) {
        if (!STATUS_PENDING.equals(order.getStatus())) {
            return;
        }
        if (order.getExpiredAt() == null || !order.getExpiredAt().isBefore(LocalDateTime.now())) {
            return;
        }
        orderRepository.closeExpiredOrder(order.getId());
    }

    private String normalizePayType(String payType) {
        String type = trimToEmpty(payType).toLowerCase(Locale.ROOT);
        if ("alipay".equals(type) || "wxpay".equals(type)) {
            return type;
        }
        return null;
    }

    private String generateOrderNo() {
        return "RC" + LocalDateTime.now().format(ORDER_NO_TIME_FMT)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    private String buildYiPayUrl(YiPayConfig config,
                                 String orderNo,
                                 String payType,
                                 BigDecimal amount,
                                 String packageName,
                                 String notifyUrl,
                                 String returnUrl,
                                 Long userId) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("pid", config.pid);
        params.put("type", payType);
        params.put("out_trade_no", orderNo);
        params.put("notify_url", notifyUrl);
        params.put("return_url", returnUrl);
        params.put("name", "字数包-" + packageName);
        params.put("money", amount.toPlainString());
        params.put("param", String.valueOf(userId));
        params.put("sign_type", "MD5");
        params.put("sign", generateSign(params, config.key));

        String query = params.entrySet()
                .stream()
                .filter(entry -> hasText(entry.getValue()))
                .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
                .collect(Collectors.joining("&"));

        return config.gatewayUrl + (config.gatewayUrl.contains("?") ? "&" : "?") + query;
    }

    private boolean verifySign(Map<String, String> params, String key) {
        String expected = generateSign(params, key);
        String actual = trimToEmpty(params.get("sign"));
        return expected.equalsIgnoreCase(actual);
    }

    private String generateSign(Map<String, String> params, String key) {
        Map<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String k = entry.getKey();
            String v = entry.getValue();
            if (!hasText(k) || !hasText(v)) {
                continue;
            }
            if ("sign".equalsIgnoreCase(k) || "sign_type".equalsIgnoreCase(k)) {
                continue;
            }
            sorted.put(k, v);
        }

        String signContent = sorted.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));

        return md5(signContent + key);
    }

    private String md5(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5算法不可用", e);
        }
    }

    private YiPayConfig loadYiPayConfig() {
        YiPayConfig config = new YiPayConfig();
        config.enabled = Boolean.parseBoolean(configService.getConfig("payment_yipay_enabled", "false"));
        config.gatewayUrl = trimToEmpty(configService.getConfig("payment_yipay_gateway_url", ""));
        config.pid = trimToEmpty(configService.getConfig("payment_yipay_pid", ""));
        config.key = trimToEmpty(configService.getConfig("payment_yipay_key", ""));
        config.notifyUrl = trimToEmpty(configService.getConfig("payment_yipay_notify_url", ""));
        config.returnUrl = trimToEmpty(configService.getConfig("payment_yipay_return_url", ""));
        config.orderExpireMinutes = parseInt(configService.getConfig("payment_order_expire_minutes", "30"), 30);
        if (config.orderExpireMinutes < 5) {
            config.orderExpireMinutes = 5;
        }
        if (config.orderExpireMinutes > 180) {
            config.orderExpireMinutes = 180;
        }
        return config;
    }

    private void validateYiPayConfig(YiPayConfig config) {
        if (!hasText(config.gatewayUrl) || !hasText(config.pid) || !hasText(config.key)) {
            throw new RuntimeException("支付参数未配置完整，请联系管理员");
        }
    }

    private String buildBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        boolean defaultPort = ("http".equalsIgnoreCase(scheme) && serverPort == 80)
                || ("https".equalsIgnoreCase(scheme) && serverPort == 443);
        String port = defaultPort ? "" : ":" + serverPort;
        return scheme + "://" + serverName + port + request.getContextPath();
    }

    private String buildDefaultReturnUrl(HttpServletRequest request, String baseUrl) {
        String origin = trimToEmpty(request.getHeader("Origin"));
        if (hasText(origin) && (origin.startsWith("http://") || origin.startsWith("https://"))) {
            return origin + "/settings";
        }
        return baseUrl;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (hasText(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (hasText(realIp)) {
            return realIp;
        }
        return request.getRemoteAddr();
    }

    private String toJson(Map<String, String> params) {
        try {
            return OBJECT_MAPPER.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            return params.toString();
        }
    }

    private int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static class YiPayConfig {
        private boolean enabled;
        private String gatewayUrl;
        private String pid;
        private String key;
        private String notifyUrl;
        private String returnUrl;
        private int orderExpireMinutes;
    }
}
