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
        return "# 核心任务\n" +
                "针对\"已优化的文本\"（指逻辑闭环、细节真实、风格流畅、目标明确的文本），按以下规则执行\"退化操作\"，使文本从\"优化状态\"回归\"原始状态\"，最后输出退化后的文本。\n\n" +
                
                "# 一、语言基础退化：失准与粗糙化\n\n" +
                
                "## 语言规范性崩坏\n" +
                "- 标点符号逻辑混乱（如引号与句子停顿脱节、逗号/句号滥用）\n" +
                "- 堆砌口语化冗余助词（如在陈述句中反复添加\"哈\"\"呢\"\"啦\"，导致表达拖沓）\n" +
                "- 句式风格割裂，保留语病与拗口表达（如将\"开着辆半新的东风日产往家赶\"退化为\"开着一辆半新的东风日产车回家去\"，通过重复量词\"辆\"、添加冗余后缀\"去\"，造成语言累赘）\n\n" +
                
                "## 语言笼统与质感流失\n" +
                "- 用\"万能词汇\"替代精准措辞（如将\"字句间的分寸、表述的严谨度\"简化为\"公文的写法\"，用模糊表述消解角色特质与场景细节）\n" +
                "- 放任重复表达，拆分同类信息（如将\"头发贴在头皮、脸上带着青春期油脂\"退化为\"头发贴着头皮，头发有点油，脸上也很油腻\"，通过重复\"油\"字、拆分同类细节，导致描述碎片化）\n\n" +
                
                "# 二、对话与叙事退化：生硬与失序\n\n" +
                
                "## 对话的语境脱节与同质化\n" +
                "- 对话脱离人物情绪与身份，语气平板无起伏（如将女人\"陡然提音量\"的请求退化为\"你能不能打个招呼，把我弟弟推荐过去\"，删除\"帮着\"\"哪能不给你面子\"等体现试探与强势的词汇，让对话失去性格辨识度）\n" +
                "- 用\"对话+解释\"的生硬结构替代情节衔接（如在\"女人被激怒\"前直接添加\"女人听了很生气，于是说\"的解释，而非通过电话里的男声自然引爆冲突，破坏叙事流畅性）\n\n" +
                
                "## 叙事节奏的失控\n" +
                "- **长短句搭配与场景节奏错位**：\n" +
                "  * 紧张场景用长句（如将车祸前的\"转过急弯，白光——'轰！'\"退化为\"在转过一个急转弯的时候，对面突然射来了一道非常刺眼的白光，然后就听到了一声巨大的'轰'的响声\"，用长句稀释冲击感）\n" +
                "  * 铺陈场景用短句（如描写教室环境时用\"周围是学生。有的睡觉。有的写卷。\"，用碎片化短句破坏舒缓氛围）\n" +
                "- **失衡\"事件与反思\"比例**：核心情节简略，空泛反思占比过高（如将相亲电话冲突简化为\"女人让陈着帮忙，陈着拒绝，女人挂电话\"，却用大段文字写\"陈着觉得考公后悔，体制内太累\"，导致反思脱离情节，成为空洞抒情）\n\n" +
                
                "# 三、逻辑与情感退化：割裂与肤浅\n\n" +
                
                "## 逻辑衔接的显性断裂\n" +
                "- 依赖生硬连接词，段落过渡脱节（如从\"挂电话\"直接跳转\"陈着回忆考公经历\"时，强行添加\"然而，陈着想起了自己的过去\"，用\"然而\"硬拉逻辑，而非通过\"握方向盘的沉默\"自然过渡）\n" +
                "- 段落首尾无闭环：开头以\"情绪\"切入，结尾以\"无关细节\"收束（如某段以\"陈着很郁闷\"开头，却以\"教室里的窗帘是蓝色的\"结尾，导致叙事链条断裂，逻辑不完整）\n\n" +
                
                "## 情感表达的直白与割裂\n" +
                "- 用直白抒情替代\"动作/细节反衬\"（如直接写\"陈着很无奈\"，而非通过\"话到嘴边又改成温和建议\"体现，让情绪失去层次感）\n" +
                "- 环境与心境脱节：用与情绪矛盾的场景细节烘托氛围（如重生后陈着心境释然，却用\"天空乌云密布，教室光线昏暗\"描写环境，导致环境与心境完全割裂，无法传递情感）\n\n" +
                
                "# 四、语境适配退化：错位与无效\n\n" +
                
                "## 功能场景的语言错位\n" +
                "- 正文叙事与角色身份脱节：体制内角色的\"肌肉反应\"用口语化表达体现（如将\"树死了，不就只剩'发财'了吗\"退化为\"树死了没事，你肯定能发财\"，失去得体感，不符合人设）\n" +
                "- 作者结语用生硬书面语（如将\"新人报到，求支持\"退化为\"本作品为作者首作，敬请各位读者予以支持\"，脱离网文语境，显得疏离）\n\n" +
                
                "## 伏笔与细节的功能性失效\n" +
                "- 添加无关细节，不服务主题（如给黄柏涵添加\"喜欢吃苹果\"的描述，既不强化高中生人设，也不铺垫后续情节，成为无效细节）\n" +
                "- 关键细节脱离核心主题（如\"重生\"设定仅用\"陈着回到了高三\"一句话带过，不通过\"转笔生涩\"\"校服触感\"等细节强化，导致\"重生\"设定缺乏说服力，也不扣\"肌肉反应\"的核心主题）\n\n" +
                
                "# 执行要求\n" +
                "- 严格遵循以上所有退化规则\n" +
                "- 不要解释，不要分析\n" +
                "- 直接输出退化后的完整文本";
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
        userMessage.put("content", "请对以下内容进行AI消痕处理：\n\n" + content);
        messages.add(userMessage);

        // 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", 8000);
        requestBody.put("temperature", 0.9);
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
    public void removeAITraceStream(String content, AIConfigRequest aiConfig, SseEmitter emitter) throws IOException {
        if (aiConfig == null || !aiConfig.isValid()) {
            throw new IOException("AI配置无效");
        }
        
        String baseUrl = aiConfig.getEffectiveBaseUrl();
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
        userMessage.put("content", "请对以下内容进行AI消痕处理：\n\n" + content);
        messages.add(userMessage);

        // 构建请求体（启用流式）
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", 8000);
        requestBody.put("temperature", 0.9);
        requestBody.put("stream", true); // 启用流式响应
        requestBody.put("messages", messages);

        try {
            String url = aiConfig.getApiUrl();
            
            logger.info("开始AI消痕流式处理，调用AI接口: {}", url);
            
            // 发送开始事件
            emitter.send(SseEmitter.event().name("start").data("开始处理"));
            
            // 使用RestTemplate进行流式读取
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(15000);
            requestFactory.setReadTimeout(120000);
            RestTemplate restTemplate = new RestTemplate(requestFactory);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            // 流式接口必须设置Accept为text/event-stream
            headers.set("Accept", "text/event-stream");

            // 使用ResponseExtractor进行真正的流式读取
            restTemplate.execute(url, HttpMethod.POST, 
                req -> {
                    req.getHeaders().putAll(headers);
                    req.getBody().write(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(requestBody));
                },
                response -> {
                    try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                        
                        String line;
                        while ((line = reader.readLine()) != null) {
                            line = line.trim();
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6);
                                if ("[DONE]".equals(data)) {
                                    break; // 流式响应结束
                                }
                                
                                try {
                                    // 解析JSON获取内容
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
                                            if (contentChunk != null) {
                                                // 发送内容块到前端
                                                Map<String, String> eventData = new HashMap<>();
                                                eventData.put("content", contentChunk);
                                                emitter.send(SseEmitter.event().data(eventData));
                                                logger.debug("发送内容块: {}", contentChunk.substring(0, Math.min(20, contentChunk.length())));
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    logger.warn("解析流式响应失败: {}", e.getMessage());
                                }
                            }
                        }
                        
                        // 完成流式响应
                        emitter.send(SseEmitter.event().data("[DONE]"));
                        emitter.complete();
                        
                    } catch (IOException e) {
                        logger.error("读取流式响应失败", e);
                        emitter.completeWithError(e);
                    }
                    return null;
                });

        } catch (Exception e) {
            logger.error("AI消痕流式调用失败", e);
            emitter.completeWithError(e);
            throw new IOException("AI消痕流式调用失败: " + e.getMessage());
        }
    }
}

