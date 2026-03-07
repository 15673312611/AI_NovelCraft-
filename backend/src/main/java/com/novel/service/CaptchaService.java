package com.novel.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 点击验证服务 - 行为分析版
 * 通过分析用户点击前的鼠标轨迹判断是否为人类
 */
@Service
public class CaptchaService {

    private final Map<String, TokenInfo> tokenStore = new ConcurrentHashMap<>();
    private final Map<String, RequestCount> ipRequestCount = new ConcurrentHashMap<>();
    
    private static final long TOKEN_EXPIRE_MS = 5 * 60 * 1000;
    private static final long VERIFIED_EXPIRE_MS = 2 * 60 * 1000;
    private static final int MAX_REQUESTS_PER_MINUTE = 30;
    
    private final SecureRandom random = new SecureRandom();

    /**
     * 生成验证token
     */
    public Map<String, Object> generateToken(String ip) {
        cleanExpiredData();
        
        // IP限流
        RequestCount count = ipRequestCount.computeIfAbsent(ip, k -> new RequestCount());
        if (count.getRequestsLastMinute() >= MAX_REQUESTS_PER_MINUTE) {
            throw new RuntimeException("请求太频繁，请稍后再试");
        }
        count.increment();
        
        // 生成token
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        
        TokenInfo info = new TokenInfo();
        info.createTime = System.currentTimeMillis();
        info.expireTime = info.createTime + TOKEN_EXPIRE_MS;
        info.verified = false;
        info.ip = ip;
        tokenStore.put(token, info);
        
        return Map.of("token", token);
    }

    /**
     * 验证点击行为
     * @param token 验证token
     * @param ip 客户端IP
     * @param mouseTrack 鼠标轨迹数据
     * @param clickTime 从页面加载到点击的时间(ms)
     */
    public boolean verify(String token, String ip, List<Map<String, Object>> mouseTrack, long clickTime) {
        if (token == null || token.isEmpty()) return false;
        
        TokenInfo info = tokenStore.get(token);
        if (info == null) return false;
        if (System.currentTimeMillis() > info.expireTime) {
            tokenStore.remove(token);
            return false;
        }
        if (info.verified) return false;
        if (!ip.equals(info.ip)) {
            tokenStore.remove(token);
            return false;
        }
        
        // 行为分析
        if (!analyzeHumanBehavior(mouseTrack, clickTime)) {
            tokenStore.remove(token);
            return false;
        }
        
        // 标记为已验证
        info.verified = true;
        info.expireTime = System.currentTimeMillis() + VERIFIED_EXPIRE_MS;
        return true;
    }

    /**
     * 分析是否为人类行为
     */
    private boolean analyzeHumanBehavior(List<Map<String, Object>> mouseTrack, long clickTime) {
        // 1. 点击时间太短（小于500ms）可能是脚本
        if (clickTime < 500) return false;
        
        // 2. 没有鼠标轨迹数据可能是脚本
        if (mouseTrack == null || mouseTrack.size() < 3) return false;
        
        // 3. 检查轨迹是否有变化（不是直线瞬移）
        boolean hasMovement = false;
        double lastX = -1, lastY = -1;
        int directionChanges = 0;
        double lastDx = 0, lastDy = 0;
        
        for (Map<String, Object> point : mouseTrack) {
            double x = getDouble(point, "x");
            double y = getDouble(point, "y");
            
            if (lastX >= 0) {
                double dx = x - lastX;
                double dy = y - lastY;
                
                if (Math.abs(dx) > 1 || Math.abs(dy) > 1) {
                    hasMovement = true;
                }
                
                // 检测方向变化
                if (lastDx != 0 || lastDy != 0) {
                    if ((dx * lastDx < 0) || (dy * lastDy < 0)) {
                        directionChanges++;
                    }
                }
                lastDx = dx;
                lastDy = dy;
            }
            lastX = x;
            lastY = y;
        }
        
        // 4. 人类操作通常有一些方向变化
        if (!hasMovement) return false;
        
        // 5. 轨迹点时间间隔检查
        long firstTime = getLong(mouseTrack.get(0), "t");
        long lastTime = getLong(mouseTrack.get(mouseTrack.size() - 1), "t");
        long duration = lastTime - firstTime;
        
        // 轨迹持续时间太短可能是脚本
        if (duration < 100) return false;
        
        return true;
    }

    /**
     * 消费token
     */
    public boolean consumeToken(String token, String ip) {
        if (token == null || token.isEmpty()) return false;
        
        TokenInfo info = tokenStore.remove(token);
        if (info == null) return false;
        
        return info.verified && System.currentTimeMillis() < info.expireTime && ip.equals(info.ip);
    }

    private void cleanExpiredData() {
        long now = System.currentTimeMillis();
        tokenStore.entrySet().removeIf(e -> now > e.getValue().expireTime);
        ipRequestCount.entrySet().removeIf(e -> e.getValue().isExpired());
    }
    
    private double getDouble(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        return 0;
    }
    
    private long getLong(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).longValue();
        return 0;
    }

    private static class TokenInfo {
        long createTime;
        long expireTime;
        boolean verified;
        String ip;
    }

    private static class RequestCount {
        private final long[] requestTimes = new long[MAX_REQUESTS_PER_MINUTE];
        private int index = 0;
        private long lastActivity = System.currentTimeMillis();

        synchronized void increment() {
            requestTimes[index] = System.currentTimeMillis();
            index = (index + 1) % requestTimes.length;
            lastActivity = System.currentTimeMillis();
        }

        synchronized int getRequestsLastMinute() {
            long oneMinuteAgo = System.currentTimeMillis() - 60_000;
            int count = 0;
            for (long t : requestTimes) if (t > oneMinuteAgo) count++;
            return count;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - lastActivity > 3600_000;
        }
    }
}
