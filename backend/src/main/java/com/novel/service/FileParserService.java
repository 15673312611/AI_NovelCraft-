package com.novel.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.rtf.RTFEditorKit;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 文件解析服务
 * 支持 txt / md / html / json / doc / docx / pdf / rtf 等格式的正文提取
 */
@Service
@Slf4j
public class FileParserService {

    /**
     * 解析纯文本文件
     */
    public String parseTxtFile(InputStream inputStream) throws Exception {
        log.info("开始解析文本文件");
        try {
            byte[] bytes = readAllBytes(inputStream);
            String content = new String(bytes, StandardCharsets.UTF_8);
            log.info("文本文件解析成功，内容长度={}", content.length());
            return content;
        } catch (Exception e) {
            log.error("文本文件解析失败", e);
            throw new Exception("文本文件解析失败: " + e.getMessage());
        }
    }

    /**
     * 解析 markdown 文件
     */
    public String parseMarkdownFile(InputStream inputStream) throws Exception {
        return parseTxtFile(inputStream);
    }

    /**
     * 解析 html 文件
     */
    public String parseHtmlFile(InputStream inputStream) throws Exception {
        try {
            String html = new String(readAllBytes(inputStream), StandardCharsets.UTF_8);
            String content = Jsoup.parse(html).text();
            log.info("HTML文件解析成功，内容长度={}", content.length());
            return content;
        } catch (Exception e) {
            log.error("HTML文件解析失败", e);
            throw new Exception("HTML文件解析失败: " + e.getMessage());
        }
    }

    /**
     * 解析 JSON 文件
     */
    public String parseJsonFile(InputStream inputStream) throws Exception {
        return parseTxtFile(inputStream);
    }

    /**
     * 解析 docx/docm 文件
     */
    public String parseDocxFile(InputStream inputStream) throws Exception {
        log.info("开始解析DOCX文件");
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder content = new StringBuilder();

            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText();
                if (text != null && !text.trim().isEmpty()) {
                    content.append(text).append("\n");
                }
            }

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

            String result = content.toString();
            log.info("DOCX文件解析成功，内容长度={}", result.length());
            return result;
        } catch (Exception e) {
            log.error("DOCX文件解析失败", e);
            throw new Exception("DOCX文件解析失败: " + e.getMessage());
        }
    }

    /**
     * 解析 doc 文件
     */
    public String parseDocFile(InputStream inputStream) throws Exception {
        try (HWPFDocument document = new HWPFDocument(inputStream);
             WordExtractor extractor = new WordExtractor(document)) {
            String content = extractor.getText();
            log.info("DOC文件解析成功，内容长度={}", content != null ? content.length() : 0);
            return content == null ? "" : content;
        } catch (Exception e) {
            log.error("DOC文件解析失败", e);
            throw new Exception("DOC文件解析失败: " + e.getMessage());
        }
    }

    /**
     * 解析 PDF 文件
     */
    public String parsePdfFile(InputStream inputStream) throws Exception {
        try (PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String content = stripper.getText(document);
            log.info("PDF文件解析成功，内容长度={}", content != null ? content.length() : 0);
            return content == null ? "" : content;
        } catch (Exception e) {
            log.error("PDF文件解析失败", e);
            throw new Exception("PDF文件解析失败: " + e.getMessage());
        }
    }

    /**
     * 解析 RTF 文件
     */
    public String parseRtfFile(InputStream inputStream) throws Exception {
        try {
            RTFEditorKit kit = new RTFEditorKit();
            DefaultStyledDocument document = new DefaultStyledDocument();
            kit.read(inputStream, document, 0);
            String content = document.getText(0, document.getLength());
            log.info("RTF文件解析成功，内容长度={}", content.length());
            return content;
        } catch (Exception e) {
            log.error("RTF文件解析失败", e);
            throw new Exception("RTF文件解析失败: " + e.getMessage());
        }
    }

    /**
     * 统计字数（中英文混合）
     */
    public int countWords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
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
            case "md":
            case "markdown":
                return parseMarkdownFile(inputStream);
            case "html":
            case "htm":
                return parseHtmlFile(inputStream);
            case "json":
                return parseJsonFile(inputStream);
            case "docx":
            case "docm":
                return parseDocxFile(inputStream);
            case "doc":
                return parseDocFile(inputStream);
            case "pdf":
                return parsePdfFile(inputStream);
            case "rtf":
                return parseRtfFile(inputStream);
            default:
                throw new Exception("不支持的文件类型: " + fileType);
        }
    }

    private byte[] readAllBytes(InputStream inputStream) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
        int nRead;
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return buffer.toByteArray();
    }
}
