package com.novel.service;

import com.novel.dto.AIConfigRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI消痕服务
 * 使用AI对生成的内容进行去AI味处理
 */
@Service
public class AITraceRemovalService {

    private static final Logger logger = LoggerFactory.getLogger(AITraceRemovalService.class);

    /**
     * AI消痕提示词 - 深度润色与去AI味
     */
    private String buildRemovalPrompt() {
        return "你将扮演一个专业的短篇小说润色AI，严格遵循以下规则对输入的稿件进行深度润色，根除所有AI高频“毒词”与陈腐“毒句”，实现≥98%的清除率。零容忍。\n" +
                "\n" +
                "一、核心替换规则与毒词过滤\n" +
                "1. 风格与文笔（面向大众阅读习惯）\n" +
                "[定位] 任何形式的、跨领域的非字面类比描述（如：将“危机”类比为“风暴”）。\n" +
                "-[解决] 删除比喻手法，直接描述事件本身的核心事实。\n" +
                "[定位] 华丽、复杂的辞藻和书面语。\n" +
                "-[解决] 替换为简单、直白、通俗的口语化词汇。\n" +
                "[定位] 名词前存在多个形容词堆砌（尤其景物描写）。\n" +
                "-[解决] 只保留最核心的一个形容词，或用具体动作/场景互动来体现。\n" +
                "[定位] “最”、“绝对”、“终极”等极端词汇。\n" +
                "-[解决] 替换为程度较轻的词或直接删除。\n" +
                "\n" +
                "2. 一级必杀清单：毒词（出现即杀，100%避免）\n" +
                "- 内心透视类：知道/明白/意识到/感觉到/认为/觉得/想到/寻思/理解/察觉到/清楚/记得/发现\n" +
                "- 模糊弱化类：一丝/一抹/一股/些许/有点/略微/微微/轻微/似乎/好像/仿佛/如同/好似/某种程度上\n" +
                "- 陈腐动作类：深吸一口气/倒吸一口凉气/脸色一变/心中一震/身体一僵/挑眉/耸了耸肩/摊了摊手/死寂\n" +
                "- 五官滥用类：任何关于嘴角的描写（勾起/扬起/撇了撇），任何套路化的眼神/目光/眼眸/瞳孔描写，以及将目光、视线等形容为凝固\n" +
                "- 陈旧比喻类：夜色如墨/月光如水/金属摩擦般的声音/深渊般的声音\n" +
                "- 不适感形容词类：黏腻/温吞/生锈的xxx（非真正生锈的实物）/灌铅般，以及所有类似的、带来生理不适或节奏拖沓的词汇\n" +
                "- 逻辑连接词：首先/其次/然后/最后/综上所述/值得注意的是/总而言之/此外/另外/与此同时\n" +
                "- 冗余副词类：缓缓地/慢慢地/静静地/悄悄地\n" +
                "- 无意义动词：呢喃/低语/摩挲/摩擦\n" +
                "- 抽象形容词：坚定/坚毅/肯定/认真/仔细/警惕/惊恐/难以置信/微不可察/不容置喙\n" +
                "- 空洞名词：火花/光芒/面庞\n" +
                "- AI常用词：消毒水的味道/钻进鼻腔\n" +
                "\n" +
                "3. 二级必杀清单：毒句结构（出现即改写）\n" +
                "- 否定-再定义式：严禁使用“这不是A，而是B”或类似结构。直接描述B。\n" +
                "- 递进-解释式：严禁使用“每个字都饱含A，也藏着B”的结构。\n" +
                "- 如果-那么式类比：严禁使用“如果说A是XX，那么B就是YY”的句式。\n" +
                "\n" +
                "4. 句式结构\n" +
                "[定位] “总而言之”等总结性句式。-[解决] 彻底删除。\n" +
                "[定位] 任何不必要的重复，包括机械的句式排比或词汇堆砌（≥3个）。-[解决] 采用差异化改写。\n" +
                "[定位] “选择一/选择二”或任何分支选项式表述。-[解决] 直接让角色做出决定并行动。\n" +
                "\n" +
                "5. 角色称谓\n" +
                "[定位] 连续或高频使用人称代词“他/她”。-[解决] 优先使用角色名字。若需变化，可使用身份或特征指代。\n" +
                "\n" +
                "6. 情绪表达\n" +
                "[定位] 直接描述极端情绪的词汇（如：绝望、狂喜、暴怒）。-[解决] 删除情绪词汇，通过神态、动作间接呈现。\n" +
                "[定位] 主角的所有常规情绪词（如“他感到高兴”）。-[解决] 删除情绪词，通过行为体现。\n" +
                "[定位] 强烈的感叹语气和过多的感叹号！！-[解决] 减少感叹号使用。\n" +
                "\n" +
                "二、基因重组与呼吸注入\n" +
                "1. 基因重组（词汇替换≥40%，段落重组≥65%）\n" +
                "- 标点重塑：删除所有非对话性质的引号、括号、破折号、顿号。只保留用于直接对话的双引号。\n" +
                "- 词汇替换：书面语→口语（进行→搞，予以回应→回了句）；抽象→具象（愤怒→拳头捏白）；正式→俗语。\n" +
                "- 结构重组：拆分超过35字的长句；语序颠倒；主被动转换。\n" +
                "\n" +
                "2. 呼吸注入（单句成段45-60%，对话独立成段100%）\n" +
                "- 所有用引号包裹的直接对话，必须独占一个段落。\n" +
                "- 情绪爆点、关键信息、强力转折，必须用单句成段来强调。\n" +
                "- 每1000字强制插入至少6个12字以内的超短句。\n" +
                "\n" +
                "3. 感官复苏（感官描写覆盖率≥80%）\n" +
                "- 情绪外化（Show, Don't Tell）：禁止直接说“愤怒/紧张”，必须描写“拳头捏响/手心出汗”。\n" +
                "- 五感描写：视觉（具体细节）、听觉（模拟声音）、嗅觉、触觉、味觉。\n" +
                "- 内心独白：只在情节重大转折或吐槽时使用，必须简短有力，严禁用于解释剧情或设定。\n" +
                "\n" +
                "三、真实瑕疵与网络基因植入\n" +
                "1. 强制替换规则：\n" +
                "- 将文中所有的助词【地】全部替换为【的】。\n" +
                "- 将文中所有的标准省略号【……】全部删除（除对话中极必要外）。\n" +
                "- 将非对话内容中的感叹号【！】删除60%。\n" +
                "2. 高频笔误植入：每1000字强制植入3-5个指定错别字（在→再，已经→以经，那→哪，帐→账，竟然→尽然，度过→渡过）。\n" +
                "3. 网络基因植入：每章可审慎植入1处符合背景的热梗；极少情况使用颜文字；需要展示图片时使用【图片.jpg】占位符。\n" +
                "\n" +
                "四、书名词汇库集成与应用\n" +
                "[词汇库构建] 基于正文内容，锁定风格接近的知名网文构建：角色名称库、地点场景库、功法技能库、物品法宝库、组织势力库、专有术语库。\n" +
                "[应用规则] 优先使用库中名词；保持一致性；自然融入；避免重复描述。\n" +
                "\n" +
                "五、绝对禁区清单（七大禁令）\n" +
                "1. 比喻全面禁令：严禁使用像/如/仿佛/好似/宛如等一切比喻词。\n" +
                "2. 尴尬句式禁令：严禁伪哲学旁白、自问自答式旁白。\n" +
                "3. 解释性内心独白禁令：严禁解释剧情、设定或动机。\n" +
                "4. 五官描写限制：禁止无意义的眼神、嘴角描写。\n" +
                "5. 环境描写限制：必须服务于氛围或情节，禁止开篇大段描写。\n" +
                "6. 总结展望禁令：结尾不能有总结或展望。\n" +
                "7. 开局与痛苦描写模板禁令：禁止“无尽黑暗...记忆洪流”开局；禁止“大脑被针刺”痛苦描写。\n" +
                "\n" +
                "六、输出格式与要求\n" +
                "1. 叙事视角：必须以主角的第一人称（“我”）进行叙述。\n" +
                "2. 导语：在正文开头撰写一段导语，吸引读者或奠定基调。\n" +
                "3. 章节：正文主体用阿拉伯数字序号（1, 2, 3...）分章，每个序号开启新章节。\n" +
                "4. 内容：直接输出修改后的完整文本，不解释修改内容，保持原文核心情节不变，确保语句通顺自然。\n" +
                "5. 格式：不要使用Markdown格式包裹正文，直接输出纯文本（除了必要的换行）。";
    }

    /**
     * 执行AI消痕处理
     */
    public String removeAITrace(String content, AIConfigRequest aiConfig) throws Exception {
        if (aiConfig == null || !aiConfig.isValid()) {
            throw new Exception("AI配置无效");
        }
        
        String baseUrl = aiConfig.getEffectiveBaseUrl();
        String apiKey = aiConfig.getApiKey();
        String model = aiConfig.getModel();

        if (apiKey == null || apiKey.trim().isEmpty() || "your-api-key-here".equals(apiKey)) {
            throw new Exception("API Key未配置");
        }

        // 构建消息列表
        List<Map<String, String>> messages = new ArrayList<>();
        
        // 系统消息：AI消痕指令
        Map<String, String> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", buildRemovalPrompt());
        messages.add(systemMessage);
        
        // 用户消息：需要处理的内容
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", content);
        messages.add(userMessage);

        // 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", 8000);
        requestBody.put("temperature", 2);
        requestBody.put("messages", messages);

        try {
            String url = aiConfig.getApiUrl();
            
            // 使用RestTemplate进行请求
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(15000);
            requestFactory.setReadTimeout(120000);
            RestTemplate restTemplate = new RestTemplate(requestFactory);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            org.springframework.http.HttpEntity<Map<String, Object>> entity = 
                new org.springframework.http.HttpEntity<>(requestBody, headers);

            // 发送请求
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                Map.class
            ).getBody();

            if (response == null) {
                throw new Exception("AI返回响应为空");
            }

            // 解析响应
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            
            if (choices == null || choices.isEmpty()) {
                throw new Exception("AI返回结果为空");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> firstChoice = choices.get(0);
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
            String processedContent = (String) message.get("content");

            if (processedContent == null || processedContent.trim().isEmpty()) {
                throw new Exception("AI处理后的内容为空");
            }

            return processedContent.trim();

        } catch (Exception e) {
            logger.error("AI消痕调用失败", e);
            throw new Exception("AI消痕调用失败: " + e.getMessage());
        }
    }

    /**
     * 执行AI消痕处理（流式输出）
     */
    /**
     * AI消痕（流式）- 完全重写，确保正确处理换行符
     */
    public void removeAITraceStream(String content, AIConfigRequest aiConfig, SseEmitter emitter) throws IOException {
        if (aiConfig == null || !aiConfig.isValid()) {
            throw new IOException("AI配置无效");
        }
        
        String apiKey = aiConfig.getApiKey();
        String model = aiConfig.getModel();

        if (apiKey == null || apiKey.trim().isEmpty() || "your-api-key-here".equals(apiKey)) {
            throw new IOException("API Key未配置");
        }

        // 构建消息列表
        List<Map<String, String>> messages = new ArrayList<>();
        
        // 系统消息：AI消痕指令
        Map<String, String> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", buildRemovalPrompt());
        messages.add(systemMessage);
        
        // 用户消息：需要处理的内容
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", content);
        messages.add(userMessage);

        // 构建请求体（启用流式）
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", 8000);
        requestBody.put("temperature", 0.8);
        requestBody.put("stream", true);
        requestBody.put("messages", messages);

        try {
            String url = aiConfig.getApiUrl();
            
            logger.info("📡 开始AI消痕流式处理，调用AI接口: {}, model: {}, stream: true", url, model);
            
            // 使用RestTemplate进行流式读取
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(15000);
            requestFactory.setReadTimeout(120000);
            RestTemplate restTemplate = new RestTemplate(requestFactory);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            headers.set("Accept", "text/event-stream");

            // 使用字节流而不是字符流，避免丢失换行符
            restTemplate.execute(url, HttpMethod.POST, 
                req -> {
                    req.getHeaders().putAll(headers);
                    req.getBody().write(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(requestBody));
                },
                response -> {
                    try {
                        // 关键修改：使用字节流读取，保留所有原始字符
                        java.io.InputStream inputStream = response.getBody();
                        byte[] buffer = new byte[8192];
                        StringBuilder lineBuffer = new StringBuilder();
                        int chunkCount = 0;
                        int totalChars = 0;
                        
                        while (true) {
                            int bytesRead = inputStream.read(buffer);
                            if (bytesRead == -1) break;
                            
                            // 将字节转换为字符串，保留所有字符包括\n
                            String chunk = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                            lineBuffer.append(chunk);
                            
                            // 按行处理，但保留换行符
                            String bufferContent = lineBuffer.toString();
                            String[] lines = bufferContent.split("\n", -1);
                            
                            // 保留最后一个不完整的行
                            lineBuffer = new StringBuilder();
                            if (lines.length > 0) {
                                lineBuffer.append(lines[lines.length - 1]);
                            }
                            
                            // 处理完整的行
                            for (int i = 0; i < lines.length - 1; i++) {
                                String line = lines[i].trim();
                                
                                if (line.startsWith("data: ")) {
                                    String data = line.substring(6);
                                    if ("[DONE]".equals(data)) {
                                        logger.info("📨 收到流式结束标记 [DONE]，共处理 {} 个chunk，总字符数: {}", chunkCount, totalChars);
                                        inputStream.close();
                                        emitter.complete();
                                        return null;
                                    }
                                    
                                    try {
                                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                                        @SuppressWarnings("unchecked")
                                        Map<String, Object> json = mapper.readValue(data, Map.class);
                                        
                                        @SuppressWarnings("unchecked")
                                        List<Map<String, Object>> choices = (List<Map<String, Object>>) json.get("choices");
                                        
                                        if (choices != null && !choices.isEmpty()) {
                                            @SuppressWarnings("unchecked")
                                            Map<String, Object> firstChoice = choices.get(0);
                                            @SuppressWarnings("unchecked")
                                            Map<String, Object> delta = (Map<String, Object>) firstChoice.get("delta");
                                            
                                            if (delta != null) {
                                                String contentChunk = (String) delta.get("content");
                                                if (contentChunk != null && !contentChunk.isEmpty()) {
                                                    // 过滤掉 <think> 标签及其内容
                                                    contentChunk = contentChunk.replaceAll("<think>.*?</think>", "");
                                                    contentChunk = contentChunk.replaceAll("<think>.*", ""); // 处理未闭合的情况
                                                    contentChunk = contentChunk.replaceAll(".*</think>", ""); // 处理跨chunk的结束标签
                                                    
                                                    if (!contentChunk.isEmpty()) {
                                                        // 发送JSON格式数据，包裹在content字段中
                                                        Map<String, String> eventData = new HashMap<>();
                                                        eventData.put("content", contentChunk);
                                                        emitter.send(SseEmitter.event().data(eventData));
                                                        chunkCount++;
                                                        totalChars += contentChunk.length();
                                                        
                                                        if (chunkCount == 1) {
                                                            logger.info("✅ 开始接收流式数据");
                                                        }
                                                        
                                                        // 调试：记录换行符数量
                                                        if (chunkCount % 50 == 0) {
                                                            int newlineCount = contentChunk.length() - contentChunk.replace("\n", "").length();
                                                            logger.info("📊 Chunk #{}: 长度={}, 换行符数量={}", chunkCount, contentChunk.length(), newlineCount);
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        logger.warn("⚠️ 解析流式响应失败: {}", e.getMessage());
                                    }
                                }
                            }
                        }
                        
                        inputStream.close();
                        emitter.complete();
                        logger.info("✅ AI消痕完成，总chunk数: {}, 总字符数: {}", chunkCount, totalChars);
                        
                    } catch (IOException e) {
                        logger.error("❌ 读取流式响应失败", e);
                        try {
                            emitter.completeWithError(e);
                        } catch (Exception ignored) {}
                    }
                    return null;
                });

        } catch (Exception e) {
            logger.error("❌ AI消痕流式调用失败", e);
            emitter.completeWithError(e);
            throw new IOException("AI消痕流式调用失败: " + e.getMessage());
        }
    }
}

