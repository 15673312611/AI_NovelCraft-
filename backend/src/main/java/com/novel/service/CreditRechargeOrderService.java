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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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

    private static final String PAY_TYPE_ALIPAY = "alipay";
    private static final String PAY_TYPE_WXPAY = "wxpay";
    private static final String PAY_TYPE_QQPAY = "qqpay";
    private static final String PAY_TYPE_CASHIER = "cashier";

    private static final Set<String> ALL_PAY_TYPES = Arrays.stream(new String[]{PAY_TYPE_ALIPAY, PAY_TYPE_WXPAY, PAY_TYPE_QQPAY, PAY_TYPE_CASHIER})
            .collect(Collectors.toSet());

    @Autowired
    private CreditPackageService creditPackageService;

    @Autowired
    private CreditRechargeOrderRepository orderRepository;

    @Autowired
    private CreditService creditService;

    @Autowired
    private SystemAIConfigService configService;

    public Map<String, Object> createOrder(Long userId, Long packageId, String payType, String redirectPath, HttpServletRequest request) {
        CreditPackage creditPackage = creditPackageService.getById(packageId);
        if (creditPackage == null || !Boolean.TRUE.equals(creditPackage.getIsActive())) {
            throw new RuntimeException("充值套餐不可用");
        }
        if (creditPackage.getPrice() == null || creditPackage.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("充值套餐金额配置异常，请联系管理员");
        }
        if (creditPackage.getCredits() == null || creditPackage.getCredits() <= 0) {
            throw new RuntimeException("充值套餐字数配置异常，请联系管理员");
        }

        YiPayConfig yiPayConfig = loadYiPayConfig();
        if (!yiPayConfig.enabled) {
            throw new RuntimeException("充值功能暂未开放");
        }
        validateYiPayConfig(yiPayConfig);

        String normalizedPayType = normalizePayType(payType, yiPayConfig.supportedTypes);
        if (normalizedPayType == null) {
            throw new RuntimeException("支付方式不支持");
        }

        String orderNo = generateOrderNo();
        int expireMinutes = yiPayConfig.orderExpireMinutes;
        String baseUrl = buildBaseUrl(request);
        String notifyUrl = hasText(yiPayConfig.notifyUrl)
                ? yiPayConfig.notifyUrl
                : baseUrl + "/auth/pay/yipay/notify";
        String frontendReturnUrl = buildFrontendReturnUrl(request, baseUrl, redirectPath);
        String returnUrl = hasText(yiPayConfig.returnUrl)
                ? yiPayConfig.returnUrl
                : buildDefaultReturnUrl(baseUrl, frontendReturnUrl);
        logger.info("创建易支付订单, orderNo={}, notifyUrl={}, returnUrl={}", orderNo, notifyUrl, returnUrl);

        BigDecimal amount = creditPackage.getPrice().setScale(2, RoundingMode.HALF_UP);
        YiPaySubmitRequest submitRequest = buildYiPaySubmitRequest(
                yiPayConfig,
                orderNo,
                normalizedPayType,
                amount,
                creditPackage.getName(),
                notifyUrl,
                returnUrl,
                userId
        );
        String payUrl = buildYiPayUrl(submitRequest.action, submitRequest.params);

        CreditRechargeOrder order = new CreditRechargeOrder();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setPackageId(creditPackage.getId());
        order.setPackageName(creditPackage.getName());
        order.setPackagePrice(amount);
        order.setPackageCredits(creditPackage.getCredits());
        order.setPaymentProvider(PROVIDER_YIPAY);
        order.setPaymentType(hasText(normalizedPayType) ? normalizedPayType : PAY_TYPE_CASHIER);
        order.setStatus(STATUS_PENDING);
        order.setPaymentUrl(payUrl);
        order.setClientIp(resolveClientIp(request));
        order.setExpiredAt(LocalDateTime.now().plusMinutes(expireMinutes));
        orderRepository.insert(order);

        return buildOrderResponse(order, submitRequest);
    }

    public Map<String, Object> getOrderForUser(Long userId, String orderNo) {
        CreditRechargeOrder order = orderRepository.findByOrderNoAndUserId(orderNo, userId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        closeIfExpired(order);
        CreditRechargeOrder latest = orderRepository.findByOrderNo(orderNo);
        return buildOrderResponse(latest == null ? order : latest, null);
    }

    public Map<String, Object> getRechargeConfigForUser() {
        YiPayConfig config = loadYiPayConfig();

        boolean configured = hasText(config.gatewayUrl) && hasText(config.pid) && hasText(config.key);
        boolean enabled = config.enabled && configured;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", enabled);
        result.put("provider", PROVIDER_YIPAY);
        result.put("supportedPayTypes", config.supportedTypes);
        result.put("defaultPayType", config.supportedTypes.isEmpty() ? PAY_TYPE_ALIPAY : config.supportedTypes.get(0));
        result.put("orderExpireMinutes", config.orderExpireMinutes);

        if (!enabled) {
            if (!config.enabled) {
                result.put("reason", "充值功能未启用");
            } else {
                result.put("reason", "支付参数配置不完整，请联系管理员");
            }
        }
        return result;
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

        if (!STATUS_PENDING.equals(order.getStatus()) && !STATUS_CLOSED.equals(order.getStatus())) {
            logger.warn("订单状态不可支付, orderNo={}, status={}", orderNo, order.getStatus());
            return false;
        }

        String callbackPid = trimToEmpty(params.get("pid"));
        if (!hasText(callbackPid) || !yiPayConfig.pid.equals(callbackPid)) {
            logger.warn("易支付回调商户PID不匹配, orderNo={}, pid={}", orderNo, callbackPid);
            return false;
        }

        String signType = trimToEmpty(params.get("sign_type"));
        if (hasText(signType) && !"MD5".equalsIgnoreCase(signType)) {
            logger.warn("易支付回调签名类型不支持, orderNo={}, signType={}", orderNo, signType);
            return false;
        }

        String tradeNo = trimToEmpty(params.get("trade_no"));
        if (!hasText(tradeNo)) {
            logger.warn("易支付回调交易号缺失, orderNo={}", orderNo);
            return false;
        }

        String orderType = trimToEmpty(order.getPaymentType()).toLowerCase(Locale.ROOT);
        String callbackType = trimToEmpty(params.get("type")).toLowerCase(Locale.ROOT);
        if (!hasText(callbackType) && !PAY_TYPE_CASHIER.equals(orderType)) {
            logger.warn("易支付回调支付方式缺失, orderNo={}", orderNo);
            return false;
        }
        if (hasText(callbackType) && hasText(orderType) && !PAY_TYPE_CASHIER.equals(orderType) && !orderType.equals(callbackType)) {
            logger.warn("易支付回调支付方式不匹配, orderNo={}, callbackType={}, orderType={}", orderNo, callbackType, orderType);
            return false;
        }

        String callbackParam = trimToEmpty(params.get("param"));
        if (hasText(callbackParam)) {
            try {
                Long callbackUserId = Long.parseLong(callbackParam);
                if (order.getUserId() != null && !order.getUserId().equals(callbackUserId)) {
                    logger.warn("易支付回调业务参数用户不匹配, orderNo={}, callbackUserId={}, orderUserId={}", orderNo, callbackUserId, order.getUserId());
                    return false;
                }
            } catch (NumberFormatException ex) {
                logger.warn("易支付回调业务参数格式异常, orderNo={}, param={}", orderNo, callbackParam);
                return false;
            }
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

        String notifyRaw = toJson(params);
        int updatedRows = orderRepository.markPaidIfUnpaid(orderNo, tradeNo, notifyRaw);
        if (updatedRows <= 0) {
            CreditRechargeOrder latest = orderRepository.findByOrderNo(orderNo);
            return latest != null && STATUS_PAID.equals(latest.getStatus());
        }

        BigDecimal credits = BigDecimal.valueOf(order.getPackageCredits() == null ? 0L : order.getPackageCredits());
        if (credits.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("充值到账失败，套餐字数异常");
        }

        String description = String.format("在线充值字数包[%s] 订单:%s", order.getPackageName(), orderNo);
        boolean recharged = creditService.recharge(order.getUserId(), credits, description, null);
        if (!recharged) {
            throw new RuntimeException("充值到账失败，请重试");
        }
        return true;
    }

    private Map<String, Object> buildOrderResponse(CreditRechargeOrder order, YiPaySubmitRequest submitRequest) {
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
        if (submitRequest != null) {
            data.put("payAction", submitRequest.action);
            data.put("payMethod", submitRequest.method);
            data.put("payParams", submitRequest.params);
        }
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

    private String normalizePayType(String payType, List<String> allowedTypes) {
        if (allowedTypes == null || allowedTypes.isEmpty()) {
            return null;
        }
        String type = trimToEmpty(payType).toLowerCase(Locale.ROOT);
        if (!hasText(type)) {
            return allowedTypes.get(0);
        }
        if (allowedTypes.contains(type)) {
            return type;
        }
        return null;
    }

    private String generateOrderNo() {
        return "RC" + LocalDateTime.now().format(ORDER_NO_TIME_FMT)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    private YiPaySubmitRequest buildYiPaySubmitRequest(YiPayConfig config,
                                                       String orderNo,
                                                       String payType,
                                                       BigDecimal amount,
                                                       String packageName,
                                                       String notifyUrl,
                                                       String returnUrl,
                                                       Long userId) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("pid", config.pid);
        if (hasText(payType) && !PAY_TYPE_CASHIER.equals(payType)) {
            params.put("type", payType);
        }
        params.put("out_trade_no", orderNo);
        params.put("notify_url", notifyUrl);
        params.put("return_url", returnUrl);
        params.put("name", "字数包-" + trimToEmpty(packageName));
        params.put("money", amount.toPlainString());
        params.put("param", userId == null ? "" : String.valueOf(userId));
        params.put("sign_type", "MD5");
        params.put("sign", generateSign(params, config.key));

        return new YiPaySubmitRequest(config.gatewayUrl, "POST", params);
    }

    private String buildYiPayUrl(String action, Map<String, String> params) {
        String query = params.entrySet()
                .stream()
                .filter(entry -> hasText(entry.getValue()))
                .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
                .collect(Collectors.joining("&"));

        return action + (action.contains("?") ? "&" : "?") + query;
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
        config.gatewayUrl = normalizeGatewayUrl(configService.getConfig("payment_yipay_gateway_url", ""));
        config.pid = trimToEmpty(configService.getConfig("payment_yipay_pid", ""));
        config.key = trimToEmpty(configService.getConfig("payment_yipay_key", ""));
        config.notifyUrl = trimToEmpty(configService.getConfig("payment_yipay_notify_url", ""));
        config.returnUrl = trimToEmpty(configService.getConfig("payment_yipay_return_url", ""));
        config.orderExpireMinutes = parseInt(configService.getConfig("payment_order_expire_minutes", "30"), 30);
        config.supportedTypes = parseSupportedTypes(configService.getConfig("payment_yipay_supported_types", "alipay,wxpay"));

        if (config.orderExpireMinutes < 5) {
            config.orderExpireMinutes = 5;
        }
        if (config.orderExpireMinutes > 180) {
            config.orderExpireMinutes = 180;
        }
        if (config.supportedTypes.isEmpty()) {
            config.supportedTypes = new ArrayList<>(Arrays.asList(PAY_TYPE_ALIPAY, PAY_TYPE_WXPAY));
        }

        return config;
    }

    private void validateYiPayConfig(YiPayConfig config) {
        if (!hasText(config.gatewayUrl) || !hasText(config.pid) || !hasText(config.key)) {
            throw new RuntimeException("支付参数未配置完整，请联系管理员");
        }
        if (!config.gatewayUrl.startsWith("http://") && !config.gatewayUrl.startsWith("https://")) {
            throw new RuntimeException("支付网关地址格式错误");
        }
        if (!config.gatewayUrl.toLowerCase(Locale.ROOT).endsWith("/submit.php")) {
            throw new RuntimeException("支付网关地址必须指向 submit.php");
        }
        if (config.supportedTypes == null || config.supportedTypes.isEmpty()) {
            throw new RuntimeException("支付方式未配置，请联系管理员");
        }
    }

    private String buildBaseUrl(HttpServletRequest request) {
        String forwardedProto = trimToEmpty(request.getHeader("X-Forwarded-Proto"));
        String forwardedHost = trimToEmpty(request.getHeader("X-Forwarded-Host"));
        String forwardedPort = trimToEmpty(request.getHeader("X-Forwarded-Port"));
        String hostHeader = trimToEmpty(request.getHeader("Host"));

        String proto = firstHeaderValue(forwardedProto);
        if (!hasText(proto)) {
            proto = trimToEmpty(request.getScheme());
        }
        if (!hasText(proto)) {
            proto = "http";
        }

        String host = firstHeaderValue(forwardedHost);
        if (!hasText(host)) {
            host = firstHeaderValue(hostHeader);
        }
        if (!hasText(host)) {
            host = trimToEmpty(request.getServerName());
        }

        if (!host.contains(":")) {
            String portValue = firstHeaderValue(forwardedPort);
            if (!hasText(portValue)) {
                int serverPort = request.getServerPort();
                if (serverPort > 0 && serverPort != 80 && serverPort != 443) {
                    portValue = String.valueOf(serverPort);
                }
            }
            if (hasText(portValue) && !isDefaultPort(proto, portValue)) {
                host = host + ":" + portValue;
            }
        }

        return proto + "://" + host + request.getContextPath();
    }

    private String buildFrontendReturnUrl(HttpServletRequest request, String baseUrl, String redirectPath) {
        String safePath = sanitizeFrontendRedirectPath(redirectPath);
        String origin = trimToEmpty(request.getHeader("Origin"));
        if (hasText(origin) && (origin.startsWith("http://") || origin.startsWith("https://"))) {
            return origin + safePath;
        }

        String contextPath = trimToEmpty(request.getContextPath());
        if (hasText(contextPath) && baseUrl.endsWith(contextPath)) {
            return baseUrl.substring(0, baseUrl.length() - contextPath.length()) + safePath;
        }
        return baseUrl + safePath;
    }

    private String buildDefaultReturnUrl(String baseUrl, String frontendReturnUrl) {
        return baseUrl + "/auth/pay/yipay/return?redirect=" + urlEncode(frontendReturnUrl);
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

    private String sanitizeFrontendRedirectPath(String redirectPath) {
        String path = trimToEmpty(redirectPath);
        if (!hasText(path)) {
            return "/settings";
        }
        if (!path.startsWith("/") || path.startsWith("//") || path.contains("\r") || path.contains("\n")) {
            return "/settings";
        }
        return path;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String firstHeaderValue(String value) {
        if (!hasText(value)) {
            return "";
        }
        return value.split(",")[0].trim();
    }

    private boolean isDefaultPort(String proto, String port) {
        if (!hasText(port)) {
            return true;
        }
        return ("http".equalsIgnoreCase(proto) && "80".equals(port))
                || ("https".equalsIgnoreCase(proto) && "443".equals(port));
    }

    private String normalizeGatewayUrl(String url) {
        String value = trimToEmpty(url);
        if (!hasText(value)) {
            return "";
        }
        try {
            URI uri = new URI(value);
            if (!hasText(uri.getScheme()) || !hasText(uri.getRawAuthority())) {
                return value;
            }
            String rawPath = trimToEmpty(uri.getPath());
            String normalizedPath = normalizeGatewayPath(rawPath);
            URI normalized = new URI(uri.getScheme(), uri.getRawAuthority(), normalizedPath, null, null);
            return normalized.toASCIIString();
        } catch (URISyntaxException ex) {
            return normalizeGatewayUrlFallback(value);
        }
    }

    private String normalizeGatewayPath(String rawPath) {
        String path = hasText(rawPath) ? rawPath : "/";
        String lowerPath = path.toLowerCase(Locale.ROOT);
        if ("/".equals(path)) {
            return "/submit.php";
        }
        if (lowerPath.endsWith("/submit.php")) {
            return path.substring(0, path.length() - "/submit.php".length()) + "/submit.php";
        }
        if (lowerPath.endsWith("/mapi.php")) {
            return path.substring(0, path.length() - "/mapi.php".length()) + "/submit.php";
        }
        if (lowerPath.endsWith("/doc.html")) {
            return path.substring(0, path.length() - "/doc.html".length()) + "/submit.php";
        }
        if (path.endsWith("/")) {
            return path + "submit.php";
        }
        if (!lowerPath.endsWith(".php")) {
            return path + "/submit.php";
        }
        return path;
    }

    private String normalizeGatewayUrlFallback(String value) {
        String normalized = value;
        if (normalized.endsWith("/")) {
            normalized = normalized + "submit.php";
        } else if (normalized.toLowerCase(Locale.ROOT).endsWith("/mapi.php")) {
            normalized = normalized.substring(0, normalized.length() - "/mapi.php".length()) + "/submit.php";
        } else if (normalized.toLowerCase(Locale.ROOT).endsWith("/doc.html")) {
            normalized = normalized.substring(0, normalized.length() - "/doc.html".length()) + "/submit.php";
        } else if (!normalized.toLowerCase(Locale.ROOT).endsWith("/submit.php") && !normalized.toLowerCase(Locale.ROOT).endsWith(".php")) {
            normalized = normalized + "/submit.php";
        }
        return normalized;
    }

    private List<String> parseSupportedTypes(String value) {
        if (!hasText(value)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (String raw : value.split(",")) {
            String item = trimToEmpty(raw).toLowerCase(Locale.ROOT);
            if (!hasText(item)) {
                continue;
            }
            if (!ALL_PAY_TYPES.contains(item)) {
                continue;
            }
            if (!result.contains(item)) {
                result.add(item);
            }
        }
        return result;
    }

    private static class YiPayConfig {
        private boolean enabled;
        private String gatewayUrl;
        private String pid;
        private String key;
        private String notifyUrl;
        private String returnUrl;
        private int orderExpireMinutes;
        private List<String> supportedTypes = new ArrayList<>();
    }

    private static class YiPaySubmitRequest {
        private final String action;
        private final String method;
        private final Map<String, String> params;

        private YiPaySubmitRequest(String action, String method, Map<String, String> params) {
            this.action = action;
            this.method = method;
            this.params = params;
        }
    }
}
