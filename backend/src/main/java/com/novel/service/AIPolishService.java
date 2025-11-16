package com.novel.service;

import com.novel.dto.AIConfigRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI润色服务
 * 专注于消除AI味，让文字更自然、更贴近真人写作风格
 */
@Service
public class AIPolishService {

    private static final Logger logger = LoggerFactory.getLogger(AIPolishService.class);

    /**
     * 构建AI润色的系统提示词
     * 核心目标：严格按照用户要求执行，让文字更日常、更像人类作家
     */
    private String buildSystemPrompt() {
        return "你是一名经验丰富的网络小说编辑，擅长让文字读起来自然、流畅、有人情味。\n" +
                "\n" +
                "【核心原则】\n" +
                "1. 用户的【润色要求】是最高优先级，必须严格遵守\n" +
                "2. 如果用户要求\"重写\"、\"改写\"、\"换一种写法\"，则完全重新创作该片段\n" +
                "3. 如果用户要求\"润色\"、\"优化\"、\"消除AI味\"，则在原文基础上优化\n" +
                "4. 如果用户有具体的风格、情感、节奏要求，必须优先满足\n" +
                "\n" +
                "【执行任务】\n" +
                "- 只处理【待润色片段】，其余上下文严格保持不变\n" +
                "- 参考【整章上下文】保持人设、世界观、叙事风格一致\n" +
                "- 根据【润色要求】的具体内容，决定是重写还是优化\n" +
                "\n" +
                "【让文字更像人类作家的核心技巧】\n" +
                "\n" +
                "1. 用日常语言，别装文艺\n" +
                "   ❌ 避免：他的内心如同暴风雨般翻涌，情绪的浪潮席卷而来\n" +
                "   ✅ 改为：他心里乱得很，一时不知道该说什么\n" +
                "   \n" +
                "   ❌ 避免：她的眼眸中闪烁着坚定的光芒\n" +
                "   ✅ 改为：她眼神很坚定\n" +
                "\n" +
                "2. 少用形容词，多用动作和细节\n" +
                "   ❌ 避免：他非常愤怒地说\n" +
                "   ✅ 改为：他一拍桌子：你说什么？\n" +
                "   \n" +
                "   ❌ 避免：她感到十分紧张\n" +
                "   ✅ 改为：她手心出汗，不自觉地攥紧了衣角\n" +
                "\n" +
                "3. 对话要像真人说话，别太工整\n" +
                "   ❌ 避免：我认为这件事情需要我们仔细考虑\n" +
                "   ✅ 改为：这事儿……咱得好好想想\n" +
                "   \n" +
                "   ❌ 避免：你的想法非常正确\n" +
                "   ✅ 改为：对，就这么办\n" +
                "\n" +
                "4. 句子长短要有变化，别太整齐\n" +
                "   ❌ 避免：他走进房间。他看到了桌子。他坐了下来。\n" +
                "   ✅ 改为：他走进房间，看到桌子，坐了下来。\n" +
                "   \n" +
                "   ❌ 避免：她感到很累，她想要休息，她躺在了床上。\n" +
                "   ✅ 改为：她累了，想休息。躺在床上，闭上眼睛。\n" +
                "\n" +
                "5. 删掉那些\"很AI\"的词\n" +
                "   ❌ 删除：然而、不过、总而言之、毋庸置疑、显而易见\n" +
                "   ❌ 删除：最、绝对、终极、完美、彻底\n" +
                "   ❌ 删除：不是A而是B、与其说A不如说B\n" +
                "   ✅ 用简单的：但是、可是、所以、因为\n" +
                "\n" +
                "6. 情绪别直说，让读者自己感受\n" +
                "   ❌ 避免：他感到绝望\n" +
                "   ✅ 改为：他愣在那里，半天说不出话\n" +
                "   \n" +
                "   ❌ 避免：她非常高兴\n" +
                "   ✅ 改为：她笑了，眼睛都弯成了月牙\n" +
                "\n" +
                "7. 别用太多比喻，尤其是跨领域的\n" +
                "   ❌ 避免：危机如同风暴般席卷而来\n" +
                "   ✅ 改为：麻烦来了\n" +
                "   \n" +
                "   ❌ 避免：他的话语如同利剑般刺入她的心脏\n" +
                "   ✅ 改为：他这话说得很重，她听了心里不舒服\n" +
                "\n" +
                "8. 称呼要自然，别老用\"他/她\"\n" +
                "   ❌ 避免：他看着她，她看着他，他对她说……\n" +
                "   ✅ 改为：张三看着李四，李四也看着他。张三开口道……\n" +
                "\n" +
                "9. 标点别乱用，引号少加\n" +
                "   ❌ 避免：他感受到了\"力量\"的涌动\n" +
                "   ✅ 改为：他感受到了力量的涌动\n" +
                "   \n" +
                "   ❌ 避免：她\"愤怒\"地说\n" +
                "   ✅ 改为：她生气地说\n" +
                "\n" +
                "10. 保持口语化，像在讲故事\n" +
                "    ❌ 避免：他迅速地做出了决定\n" +
                "    ✅ 改为：他很快就决定了\n" +
                "    \n" +
                "    ❌ 避免：她立即采取了行动\n" +
                "    ✅ 改为：她马上就动手了\n" +
                "\n" +
                "【重要：不要过度修改】\n" +
                "⚠️ 不要修改\"的\"和\"地\"的使用，保持原文\n" +
                "⚠️ 不要为了通顺而随意添加字词（如\"了\"、\"着\"、\"过\"等）\n" +
                "⚠️ 只在文字完全不通、无法理解时才调整语序和用词\n" +
                "⚠️ 如果原文已经能看懂，即使不够完美也不要改\n" +
                "⚠️ 宁可保持原文风格，也不要过度润色\n" +
                "\n" +
                "【输出要求】\n" +
                "- 直接输出润色后的文本，不要任何解释\n" +
                "- 不要用引号包裹输出内容\n" +
                "- 不要说\"这是润色后的版本\"之类的话\n" +
                "- 保持原文的情节和意思，只改表达方式\n" +
                "- 尽量少改，只改必须改的地方";
    }

    /**
     * 润色选中的文本片段
     *
     * @param chapterTitle 章节标题
     * @param fullContent 整章内容（作为上下文）
     * @param targetSelection 待润色的片段
     * @param userInstructions 用户的润色要求
     * @param aiConfig AI配置
     * @return 润色后的文本
     */
    public String polishSelection(String chapterTitle, String fullContent, String targetSelection, 
                                   String userInstructions, AIConfigRequest aiConfig) {
        try {
            // 构建用户消息 - 用户要求放在最前面，突出优先级
            StringBuilder userBuilder = new StringBuilder();
            
            // 1. 用户要求（最高优先级，放在最前面）
            userBuilder.append("【润色要求】（最高优先级，必须严格遵守）\n");
            if (userInstructions != null && !userInstructions.trim().isEmpty()) {
                userBuilder.append(userInstructions.trim());
            } else {
                userBuilder.append("消除AI味，让文字更自然流畅，避免华丽辞藻和模板化表达，保持口语化风格。");
            }
            userBuilder.append("\n\n");
            
            // 2. 待处理的片段
            userBuilder.append("【待润色片段】\n");
            userBuilder.append(targetSelection.trim());
            userBuilder.append("\n\n");
            
            // 3. 上下文信息（用于保持一致性）
            if (chapterTitle != null && !chapterTitle.trim().isEmpty()) {
                userBuilder.append("【章节标题】\n").append(chapterTitle.trim()).append("\n\n");
            }
            
            userBuilder.append("【整章上下文】（仅供参考，保持风格一致）\n");
            userBuilder.append(fullContent == null ? "" : fullContent.trim());
            
            userBuilder.append("\n\n---\n请严格按照【润色要求】执行，直接输出处理后的片段文本，不要任何解释或备注。");

            // 调用AI
            String polished = callAI(aiConfig, buildSystemPrompt(), userBuilder.toString());
            
            logger.info("✅ AI润色完成，原始长度: {}, 润色后长度: {}", 
                    targetSelection.length(), polished.length());
            
            return polished.trim();
            
        } catch (Exception e) {
            logger.error("AI润色失败", e);
            throw new RuntimeException("AI润色失败: " + e.getMessage(), e);
        }
    }

    /**
     * 调用AI接口（非流式）
     */
    private String callAI(AIConfigRequest aiConfig, String systemPrompt, String userMessage) {
        try {
            // 构建请求
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(aiConfig.getApiKey());
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", aiConfig.getModel());
            requestBody.put("max_tokens", 8000);
            requestBody.put("temperature", 0.7);
            requestBody.put("stream", false);
            
            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);
            messages.add(systemMsg);
            
            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);
            messages.add(userMsg);
            
            requestBody.put("messages", messages);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            // 使用 getApiUrl() 方法获取完整的API地址
            String apiUrl = aiConfig.getApiUrl();
            
            logger.info("🔄 调用AI润色接口: {}, model: {}", apiUrl, aiConfig.getModel());
            
            // 创建RestTemplate
            RestTemplate restTemplate = new RestTemplate();
            
            // 发送请求并获取响应
            ResponseEntity<Map> responseEntity = restTemplate.postForEntity(
                apiUrl, entity, Map.class);
            
            // 检查HTTP状态码
            if (!responseEntity.getStatusCode().is2xxSuccessful()) {
                logger.error("❌ AI接口返回错误状态码: {}", responseEntity.getStatusCode());
                throw new RuntimeException("AI接口返回错误状态码: " + responseEntity.getStatusCode());
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = responseEntity.getBody();
            
            if (response == null) {
                logger.error("❌ AI接口返回空响应");
                throw new RuntimeException("AI接口返回空响应");
            }
            
            // 检查是否有错误信息
            if (response.containsKey("error")) {
                Object errorObj = response.get("error");
                String errorMsg = errorObj != null ? errorObj.toString() : "未知错误";
                logger.error("❌ AI接口返回错误: {}", errorMsg);
                throw new RuntimeException("AI接口返回错误: " + errorMsg);
            }
            
            if (!response.containsKey("choices")) {
                logger.error("❌ AI接口返回数据格式错误，缺少choices字段: {}", response);
                throw new RuntimeException("AI接口返回数据格式错误");
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) {
                logger.error("❌ AI接口未返回有效内容");
                throw new RuntimeException("AI接口未返回有效内容");
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            if (message == null) {
                logger.error("❌ AI接口返回的message为空");
                throw new RuntimeException("AI接口返回的message为空");
            }
            
            String content = (String) message.get("content");
            
            if (content == null || content.trim().isEmpty()) {
                logger.error("❌ AI返回内容为空");
                throw new RuntimeException("AI返回内容为空");
            }
            
            logger.info("✅ AI润色接口调用成功，返回内容长度: {}", content.length());
            return content;
            
        } catch (HttpClientErrorException e) {
            logger.error("❌ AI接口HTTP客户端错误: status={}, body={}", 
                e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("AI接口调用失败(HTTP " + e.getStatusCode() + "): " + 
                e.getResponseBodyAsString());
        } catch (HttpServerErrorException e) {
            logger.error("❌ AI接口HTTP服务器错误: status={}, body={}", 
                e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("AI接口服务器错误(HTTP " + e.getStatusCode() + "): " + 
                e.getResponseBodyAsString());
        } catch (ResourceAccessException e) {
            logger.error("❌ AI接口网络连接错误", e);
            throw new RuntimeException("无法连接到AI服务: " + e.getMessage());
        } catch (Exception e) {
            logger.error("❌ AI接口调用异常", e);
            throw new RuntimeException("AI接口调用失败: " + e.getMessage());
        }
    }
}

