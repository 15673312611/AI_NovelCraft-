package com.novel.service;

import com.novel.dto.AIConfigRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import javax.annotation.PreDestroy;

import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

// 引入同包内辅助类
import static com.novel.service.HttpJson.post;
import static com.novel.service.HttpJson.read;

@Service
public class AdjectiveMiningService {

    private static final Logger logger = LoggerFactory.getLogger(AdjectiveMiningService.class);
    
    // 并发调用AI的线程池，固定10个线程
    private final ExecutorService aiCallExecutor = Executors.newFixedThreadPool(10, r -> {
        Thread t = new Thread(r, "AI-Mining-Thread");
        t.setDaemon(true);
        return t;
    });

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 应用关闭时正确关闭线程池
     */
    @PreDestroy
    public void destroy() {
        aiCallExecutor.shutdown();
        try {
            if (!aiCallExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                aiCallExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            aiCallExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 主流程：循环调用AI生成可疑形容词→解析→去重→入库
     */
    public Map<String, Object> mineAdjectives(AIConfigRequest aiConfig, int loops, int batchSize) {
        return mineTerms("adjective", aiConfig, loops, batchSize);
    }

    public Map<String, Object> mineTerms(String category, AIConfigRequest aiConfig, int loops, int batchSize) {
        if (aiConfig == null || !aiConfig.isValid()) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "AI配置无效");
            return error;
        }
        
        String baseUrl = aiConfig.getEffectiveBaseUrl();
        String apiKey = aiConfig.getApiKey();
        String model = aiConfig.getModel();

        // 使用原子类确保线程安全的计数
        AtomicInteger totalCalls = new AtomicInteger(0);
        AtomicInteger totalExtracted = new AtomicInteger(0);
        AtomicInteger totalInserted = new AtomicInteger(0);
        AtomicInteger totalSkipped = new AtomicInteger(0);
        
        // 使用线程安全的集合进行去重
        Set<String> dedupSet = Collections.synchronizedSet(new HashSet<>());
        String batchId = UUID.randomUUID().toString().replace("-", "");

        // 创建并发任务列表
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        // 将loops分批，每10个一组并发执行
        int concurrency = 10;
        for (int batch = 0; batch < loops; batch += concurrency) {
            int batchEnd = Math.min(batch + concurrency, loops);
            
            // 为当前批次创建并发任务
            for (int i = batch; i < batchEnd; i++) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    totalCalls.incrementAndGet();
                    List<String> words = Collections.emptyList();
                    
                    try {
                        // 调用AI
                        Map<String, Object> req = new HashMap<>();
                        req.put("model", model);
                        req.put("max_tokens", 2000);
                        req.put("temperature", 1.0);
                        List<Map<String, String>> messages = new ArrayList<>();
                        messages.add(msg("system", buildSystemPrompt(category)));
                        messages.add(msg("user", buildUserPrompt(category, batchSize)));
                        req.put("messages", messages);

                        Map<String, Object> resp = post(aiConfig.getApiUrl(), apiKey, req);
                        words = parseWordsFromResponse(resp);
                    } catch (Exception e) {
                        logger.warn("AI调用失败，跳过本次: {}", e.getMessage());
                        return;
                    }

                    if (words == null || words.isEmpty()) {
                        totalSkipped.incrementAndGet();
                        return; // 提取不到指定格式，跳过不报错
                    }

                    totalExtracted.addAndGet(words.size());
                    
                    // 入库（去重）- 使用同步块确保数据库操作的线程安全
                    synchronized (this) {
                        for (String w : words) {
                            String word = normalize(w);
                            if (word.isEmpty()) continue;
                            String key = word + "|zh-CN";

                            try {
                                jdbcTemplate.update(
                                    "INSERT INTO ai_adjectives_raw(word, lang, category, source_model, batch_id) VALUES(?,?,?,?,?)",
                                    word, "zh-CN", (category==null?"adjective":category), model, batchId
                                );
                            } catch (Exception e) {
                                logger.warn("插入明细失败(忽略): {}", e.getMessage());
                            }

                            if (dedupSet.contains(key)) continue;
                            dedupSet.add(key);

                            try {
                                int inserted = jdbcTemplate.update(
                                    "INSERT IGNORE INTO ai_adjectives(word, lang, category, hash, source_model) VALUES(?,?,?,?,?)",
                                    word, "zh-CN", (category==null?"adjective":category), sha256(word), model
                                );
                                totalInserted.addAndGet(inserted);
                            } catch (Exception e) {
                                logger.warn("插入词库失败(忽略): {}", e.getMessage());
                            }
                        }
                    }
                }, aiCallExecutor);
                
                futures.add(future);
            }
            
            // 等待当前批次完成再开始下一批次，避免过多并发
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
                futures.clear(); // 清空已完成的任务
            } catch (Exception e) {
                logger.error("等待并发任务完成时出错: {}", e.getMessage());
            }
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("calls", totalCalls.get());
        summary.put("extracted", totalExtracted.get());
        summary.put("inserted", totalInserted.get());
        summary.put("skipped", totalSkipped.get());
        return summary;
    }

    /**
     * 手动导入词条
     */
    public Map<String, Object> importTerms(String category, String lang, List<String> words) {
        int total = 0, inserted = 0;
        if (words == null) words = Collections.emptyList();
        for (String w : words) {
            total++;
            String word = normalize(w);
            if (word.isEmpty()) continue;
            try {
                int n = jdbcTemplate.update(
                    "INSERT IGNORE INTO ai_adjectives(word, lang, category, hash) VALUES(?,?,?,?)",
                    word, (lang==null||lang.isEmpty()?"zh-CN":lang), (category==null?"adjective":category), sha256(word)
                );
                inserted += n;
            } catch (Exception e) {
                logger.warn("导入失败(忽略): {}", e.getMessage());
            }
        }
        Map<String, Object> r = new HashMap<>();
        r.put("total", total); r.put("inserted", inserted);
        return r;
    }

    private static Map<String, String> msg(String role, String content) {
        Map<String, String> m = new HashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    // 20种随机身份（覆盖多类型网文）
    private static final String[] PERSONAS = new String[]{
        "玄幻小说大神","仙侠金牌作者","都市主笔","商战写手","职业电竞文作者",
        "末世求生题材老作者","热血校园文作者","历史小说架构师","科幻题材主创","诡秘风格写作者",
        "惊悚写作达人","权谋/宫斗写作老师","东方玄幻文主笔","武道修真老司机","奇幻冒险系写手",
        "轻松甜宠向达人","科幻/近未来作者","都市异能题材作者","探险冒险题材作者","群像/群戏调度高手"
    };

    private String randomPersona() {
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        return PERSONAS[r.nextInt(PERSONAS.length)];
    }

    private String buildSystemPrompt(String category) {
        String persona = randomPersona();
        String scope;
        switch (category == null ? "adjective" : category) {
            case "expression":
                scope = "例如“嘴角勾起一抹玩味的弧度”，“全场死一般的寂静”等形容词）";
                break;
            case "atmosphere":
                scope = "【环境氛围】常见表达（如：死一般的寂静、炸开了锅、鸦雀无声、无形的威压）";
                break;
            case "abstract":
                scope = "【抽象名词/宏大空词】（如：命运、宿命、因果、造化、苍穹、心魔）";
                break;
            case "verb":
                scope = "【高频通用动词/词组】（如：浮现、涌动、肆虐、诠释、勾勒）";
                break;
            case "sentence_starter":
                scope = "【句首模板/起手式】（如：随着…、仿佛…、宛如…、不经意间…）";
                break;
            default:
                scope = "【形容词/描述词】（如：黏腻、温吞、空洞、荒凉、诡异）";
        }
        return "你现在的身份是" + persona + "。以写作直觉，列出在该类型网文里经常出现、读者熟悉、出场率高的词条。\n" +
               "范围：" + scope + "。只返回词条本身，不要解释。";
    }

    private String buildUserPrompt(String category, int batchSize) {
        String catName;
        switch (category == null ? "adjective" : category) {
            case "expression": catName = "人物神态/动作短语"; break;
            case "atmosphere": catName = "环境氛围短语"; break;
            case "abstract": catName = "抽象名词"; break;
            case "verb": catName = "通用动词/词组"; break;
            case "sentence_starter": catName = "句首模板短语"; break;
            default: catName = "形容词/描述词"; break;
        }
        String[] heads = new String[]{"请一次性输出","本轮请给出","直接给我","只返回","仅输出"};
        String[] tails = new String[]{"，JSON数组即可。","，务必是纯JSON数组。","，只要JSON数组，别加任何解释。"};
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        String head = heads[r.nextInt(heads.length)];
        String tail = tails[r.nextInt(tails.length)];

        return head + batchSize + "条中文" + catName + tail + "\n" +
               "示例:[\"嘴角勾起一抹玩味的弧度\",\"全场死一般的寂静\"]\n" +
               "约束：\n" +
               "- 只输出纯JSON数组，不要任何额外说明(一定要按照json格式从[开始不然识别不了)\n" +
               "- 避免重复、避免包含空格或标点\n" ;
    }

    @SuppressWarnings("unchecked")
    private List<String> parseWordsFromResponse(Map<String, Object> resp) {
        try {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
            if (choices == null || choices.isEmpty()) return Collections.emptyList();
            Map<String, Object> first = choices.get(0);
            Map<String, Object> message = (Map<String, Object>) first.get("message");
            String content = String.valueOf(message.get("content"));
            // 只接受纯JSON数组；若不是则跳过
            content = content.trim();
            if (!content.startsWith("[") || !content.endsWith("]")) return Collections.emptyList();
            List<Object> arr = (List<Object>) read(content, List.class);
            List<String> out = new ArrayList<>();
            for (Object o : arr) {
                if (o instanceof String) {
                    String s = normalize((String) o);
                    if (!s.isEmpty()) out.add(s);
                }
            }
            // 去重
            LinkedHashSet<String> dedup = new LinkedHashSet<>(out);
            return new ArrayList<>(dedup);
        } catch (Exception e) {
            logger.warn("解析AI返回失败，跳过本次: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private String normalize(String s) {
        if (s == null) return "";
        String t = s.replaceAll("[\\s\\p{Punct}]", "").trim();
        if (t.length() < 1 || t.length() > 12) return "";
        return t;
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}


