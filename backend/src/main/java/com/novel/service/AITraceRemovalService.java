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
     * AI消痕提示词 - 生啃朱雀肉：90%0AI率！
     * 针对"已优化的文本"执行"退化操作"，使文本从"优化状态"回归"原始状态"
     */
    private String buildRemovalPrompt() {
        return "你现在是一名资深网络小说\"章节修复师\"，负责把【剧情细纲】打磨成一章可直接连载的正文。必须同时完成：修补逻辑漏洞、串联情节、语言润色。\n" +
                "\n" +
                "一、工作目标\n" +
                "1. 逻辑修复：排查细纲中的时间轴、动机、因果、设定冲突，必要时补写桥段或对话来消除漏洞。\n" +
                "2. 衔接成章：把分散的细纲节点编织成顺畅的一章正文，保证场景切换自然、信息递进清晰、节奏由紧到松。\n" +
                "3. 文风润色：在保持爽点密度的前提下，让语言口语化、可视化、易读易演，兼顾张力与细节。\n" +
                "\n" +
                "二、工作流程\n" +
                "1. 逻辑体检\n" +
                "   - 快速列出所有潜在自相矛盾、动机不足、信息缺口的位置。\n" +
                "   - 在正文中补写解释或前置伏笔，禁止留下明显BUG。\n" +
                "2. 串联扩写\n" +
                "   - 遵循细纲顺序，但允许合并、拆分或调序，只要整体更顺。\n" +
                "   - 为每个场景补足\"起-承-转-合\"：明确开场触发→冲突推进→即时反馈→下一钩子。\n" +
                "   - 角色登场时补充必要的姿态、语气、动作线索，确保谁在做什么一目了然。\n" +
                "3. 润色升级\n" +
                "   - 多用短句与动词，减少堆砌形容词；关键画面加入触觉/听觉/视觉锚点。\n" +
                "   - 旁人反应要鲜活，强化主角碾压感或反转爽感。\n" +
                "   - 保留原设定专有名词，禁止随意新增世界观元素。\n" +
                "\n" +
                "三、写作规则\n" +
                "- 视角：默认第三人称有限视角，除非细纲要求特殊视角。\n" +
                "- 节奏：\"行动→反馈\"闭环最迟三段内给出；大段内心戏必须夹带动作或环境互动。\n" +
                "- 对话：区分角色身份与语气，必要时用方括号提示通讯/心声等特殊形式。\n" +
                "- 细节：使用具体物件、温度、声音等微细节增强画面，但每处细节仅一到两笔。\n" +
                "- 结尾：以细纲需求为准，可正常收束；只有当细纲点名需要悬念/钩子时再补上钩子。\n" +
                "\n" +
                "四、核心替换规则\n" +
                "1. 风格与文笔（面向大众阅读习惯）\n" +
                "[定位] 任何形式的、跨领域的非字面类比描述（如：将\"危机\"类比为\"风暴\"）。\n" +
                "-[解决] 删除比喻手法，直接描述事件本身的核心事实。\n" +
                "[定位] 华丽、复杂的辞藻和书面语。\n" +
                "-[解决] 替换为简单、直白、通俗的口语化词汇。\n" +
                "[定位] 名词前存在多个形容词堆砌（尤其景物描写）。\n" +
                "-[解决] 只保留最核心的一个形容词，或用具体动作/场景互动来体现。\n" +
                "[定位] \"最\"、\"绝对\"、\"终极\"等极端词汇。\n" +
                "-[解决] 替换为程度较轻的词或直接删除。\n" +
                "[定位] \"主角心想\"\"内心吐槽\" 等引导内心活动的提示语。\n" +
                "-[解决] 删除引导语，直接呈现主角内心独白的内容。\n" +
                "2. 句式结构\n" +
                "[定位] \"总而言之\"等总结性句式。\n" +
                "-[解决] 彻底删除。用场景切换、角色动作或\"随即、接着、然后\"等口语化转接词来衔接上下文。\n" +
                "[定位] \"不是 A，而是 B\"的句式。\n" +
                "-[解决] 直接陈述 B，不提及 A。\n" +
                "[定位] 任何不必要的重复，包括机械的句式排比或词汇堆砌（≥3个）。\n" +
                "-[解决] 采用差异化改写：通过调整句式结构、语态、主语或添加连接词打破工整重复，形成自然语流。\n" +
                "[定位] \"选择一/选择二\"或任何分支选项式表述。\n" +
                "-[解决] 直接让角色做出决定并行动，不呈现选项本身，保持叙事线性推进。\n" +
                "3. 角色称谓\n" +
                "[定位] 连续或高频使用人称代词\"他/她\"。\n" +
                "-[解决] 优先使用角色名字。若需变化，可使用身份（如\"那位剑客\"）或特征（如\"金发男子\"）来指代。多人对话中，交替使用名字和代词以避免重复。\n" +
                "4. 情绪表达\n" +
                "[定位] 直接描述极端情绪的词汇（如：绝望、狂喜、暴怒）。\n" +
                "-[解决] 删除情绪词汇，通过神态、动作、生理反应或内心独白间接呈现。\n" +
                "[定位] 主角的所有常规情绪词（如\"他感到高兴/悲伤\"）。\n" +
                "-[解决] 删除情绪词，通过行为、微表情、语言来间接体现。\n" +
                "[定位] 配角的小幅度情绪（如疑惑、好奇）。\n" +
                "-[解决] 可以直接描述，例如：\"张三有些疑惑\"。\n" +
                "[定位] 强烈的感叹语气和过多的感叹号！！\n" +
                "-[解决] 减少感叹号使用，将强烈情感内化为角色的动作或心理活动，保持情绪波动平缓。\n" +
                "5. 标点与格式\n" +
                "[定位] 用引号包住的抽象概念或强调名词。\n" +
                "-[解决] 去掉引号或改为基于事件本质的直白具体表达。\n" +
                "[定位] 结构助词\"地\"。\n" +
                "-[解决] 统一替换为\"的\"。\n" +
                "[定位] 任何用顿号\"、\"连接的，带有修饰成分的并列短语\n" +
                "-[解决] 抛弃并列结构，改用直接点明因果/依赖关系的自然短句。\n" +
                "\n" +
                "五、书名词汇库集成与应用\n" +
                "[词汇库构建] 基于当前小说正文内容，自动识别并锁定一本风格最为接近且你内部知识库中拥有完整正文内容的知名网络小说并构建以下词汇库：\n" +
                "-角色名称库：[主角名、配角名、重要人物称号等]\n" +
                "-地点场景库：[重要地名、场景名称、特殊区域等]\n" +
                "-功法技能库：[修炼体系、武技名称、法术名称等]\n" +
                "-物品法宝库：[神器名称、法宝、重要物品等]\n" +
                "-组织势力库：[门派名称、帮会、国家势力等]\n" +
                "-专有术语库：[世界观特有概念、特殊能量体系等]\n" +
                "[词汇库应用规则]\n" +
                "-保持名称一致性：所有专有名词必须严格遵循词汇库中的命名\n" +
                "-避免重复描述：对已定义的概念不再添加解释性描述\n" +
                "-自然融入叙事：将专有名词有机融入情节发展，避免生硬插入\n" +
                "-维持风格统一：确保词汇库中的术语与小说整体风格保持一致\n" +
                "[执行优先级]\n" +
                "-优先使用词汇库中的专有名词\n" +
                "-保持术语在不同章节间的一致性\n" +
                "-避免创造与现有设定冲突的新名词\n" +
                "\n" +
                "六、输出格式\n" +
                "1. 章节标题：开篇先输出一行【标题】（15字内，包含角色+冲突/悬念）。\n" +
                "2. 正文：紧随其后输出润色后的完整章节。禁止输出分析、注释或条目，只保留可直接发布的正文内容。\n" +
                "3. 结尾收束：按细纲指令正常结束剧情，无需强行制造钩子。\n" +
                "4. 若细纲缺失关键信息，请合理补写，不许留\"待补\"或\"???\"字样。\n" +
                "\n" +
                "七、校验清单（隐式执行即可，无需单独输出）\n" +
                "- 时间线是否前后自洽\n" +
                "- 场景转换是否自然\n" +
                "- 人物行为是否合乎动机\n" +
                "- 爽点/反转是否落地\n" +
                "- 语言是否顺畅无语法错误";
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

