package com.novel.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试JSON引号清理功能
 */
public class JsonQuoteCleanerTest {
    
    /**
     * 模拟VolumeChapterOutlineService中的cleanJsonQuotes方法
     */
    private String cleanJsonQuotes(String json) {
        if (json == null) return null;
        
        StringBuilder result = new StringBuilder(json.length() + 100);
        boolean inString = false;
        char prevChar = 0;
        
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            
            if (c == '"' && prevChar != '\\') {
                inString = !inString;
                result.append(c);
            }
            else if (c == '\u201C' || c == '\u201D') {
                if (inString) {
                    result.append("\\\"");
                } else {
                    result.append('"');
                }
            }
            else if (c == '\uFF02') {
                if (inString) {
                    result.append("\\\"");
                } else {
                    result.append('"');
                }
            }
            else if (c == '\u2018' || c == '\u2019') {
                result.append('\'');
            }
            else {
                result.append(c);
            }
            
            prevChar = c;
        }
        
        return result.toString();
    }
    
    @Test
    public void testCleanChineseQuotesInStringValue() {
        // 测试字符串值内部的中文引号（使用Unicode转义）
        String input = "\"白苏婉当众宣布解除婚约，称这场订婚宴是送给他们的\u201C贺礼\u201D。\"";
        String expected = "\"白苏婉当众宣布解除婚约，称这场订婚宴是送给他们的\\\"贺礼\\\"。\"";
        String result = cleanJsonQuotes(input);
        
        System.out.println("Input:    " + input);
        System.out.println("Expected: " + expected);
        System.out.println("Result:   " + result);
        
        assertEquals(expected, result);
    }
    
    @Test
    public void testCompleteJsonObject() {
        // 测试完整的JSON对象（使用Unicode转义）
        String input = "{\n" +
                "  \"direction\": \"重生于订婚宴，女主当众撕毁婚约，引爆第一颗炸弹。\",\n" +
                "  \"keyPlotPoints\": [\n" +
                "    \"白苏婉当众宣布解除婚约，称这场订婚宴是送给他们的\u201C贺礼\u201D。\"\n" +
                "  ]\n" +
                "}";
        
        String result = cleanJsonQuotes(input);
        System.out.println("Cleaned JSON:\n" + result);
        
        // 验证结果中不包含未转义的中文引号
        assertFalse(result.contains("\u201C") || result.contains("\u201D"), 
            "Result should not contain unescaped Chinese quotes");
        
        // 验证可以被Jackson解析
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.readValue(result, java.util.Map.class);
            System.out.println("✅ JSON解析成功！");
        } catch (Exception e) {
            fail("JSON should be parseable after cleaning: " + e.getMessage());
        }
    }
}
