package com.novel.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 文件解析服务
 * 支持txt和docx文件解析
 */
@Service
@Slf4j
public class FileParserService {

    /**
     * 解析文本文件
     */
    public String parseTxtFile(InputStream inputStream) throws Exception {
        log.info("开始解析TXT文件");
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[1024];
            int nRead;
            while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            buffer.flush();
            byte[] bytes = buffer.toByteArray();
            String content = new String(bytes, StandardCharsets.UTF_8);
            log.info("TXT文件解析成功，内容长度: {}", content.length());
            return content;
        } catch (Exception e) {
            log.error("TXT文件解析失败", e);
            throw new Exception("TXT文件解析失败: " + e.getMessage());
        }
    }

    /**
     * 解析docx文件
     */
    public String parseDocxFile(InputStream inputStream) throws Exception {
        log.info("开始解析DOCX文件");
        try {
            XWPFDocument document = new XWPFDocument(inputStream);
            StringBuilder content = new StringBuilder();

            // 提取段落
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText();
                if (text != null && !text.trim().isEmpty()) {
                    content.append(text).append("\n");
                }
            }

            // 提取表格
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        String text = cell.getText();
                        if (text != null && !text.trim().isEmpty()) {
                            content.append(text).append("\t");
                        }
                    }
                    content.append("\n");
                }
            }

            document.close();
            
            String result = content.toString();
            log.info("DOCX文件解析成功，内容长度: {}", result.length());
            return result;
        } catch (Exception e) {
            log.error("DOCX文件解析失败", e);
            throw new Exception("DOCX文件解析失败: " + e.getMessage());
        }
    }

    /**
     * 统计字数（中英文混合）
     */
    public int countWords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        
        // 移除空白字符后计算长度
        String cleaned = text.replaceAll("\\s+", "");
        return cleaned.length();
    }

    /**
     * 根据文件类型解析文件
     */
    public String parseFile(InputStream inputStream, String fileType) throws Exception {
        if (fileType == null) {
            throw new Exception("文件类型不能为空");
        }

        fileType = fileType.toLowerCase();
        switch (fileType) {
            case "txt":
                return parseTxtFile(inputStream);
            case "docx":
                return parseDocxFile(inputStream);
            default:
                throw new Exception("不支持的文件类型: " + fileType);
        }
    }
}

