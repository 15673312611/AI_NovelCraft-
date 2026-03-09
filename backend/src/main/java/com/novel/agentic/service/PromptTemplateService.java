package com.novel.agentic.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service("agenticPromptTemplateService")
public class PromptTemplateService {

    private static final Logger logger = LoggerFactory.getLogger(PromptTemplateService.class);
    private static final String CLASSPATH_PREFIX = "classpath:prompts_output/";
    private static final Charset GB18030 = Charset.forName("GB18030");
    private static final Path[] FALLBACK_DIRS = new Path[] {
        Paths.get("prompts_output"),
        Paths.get("backend", "prompts_output"),
        Paths.get("..", "backend", "prompts_output")
    };

    private final ResourceLoader resourceLoader;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @Autowired
    public PromptTemplateService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public String loadTemplate(String fileName) {
        return cache.computeIfAbsent(fileName, this::readTemplateInternal);
    }

    private String readTemplateInternal(String fileName) {
        try {
            Resource resource = resourceLoader.getResource(CLASSPATH_PREFIX + fileName);
            if (resource.exists()) {
                return decodeTemplateBytes(resource.getInputStream().readAllBytes(), fileName, CLASSPATH_PREFIX + fileName);
            }
        } catch (IOException | IllegalArgumentException e) {
            logger.warn("Failed to load prompt template from classpath: {}", fileName, e);
        }

        for (Path dir : FALLBACK_DIRS) {
            try {
                Path path = dir.resolve(fileName);
                if (Files.exists(path)) {
                    return decodeTemplateBytes(Files.readAllBytes(path), fileName, path.toString());
                }
            } catch (IOException | IllegalArgumentException e) {
                logger.error("Failed to load prompt template from fallback directory: {} -> {}", fileName, dir, e);
            }
        }

        throw new IllegalArgumentException("Unable to load prompt template: " + fileName);
    }

    private String decodeTemplateBytes(byte[] bytes, String fileName, String source) {
        if (bytes.length >= 3
            && (bytes[0] & 0xFF) == 0xEF
            && (bytes[1] & 0xFF) == 0xBB
            && (bytes[2] & 0xFF) == 0xBF) {
            return decodeStrict(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            logger.warn("Prompt template decoded as UTF-16LE with BOM: {}", source);
            return decodeStrict(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            logger.warn("Prompt template decoded as UTF-16BE with BOM: {}", source);
            return decodeStrict(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        }

        try {
            return decodeStrict(bytes, 0, bytes.length, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException utf8Error) {
            try {
                logger.warn("Prompt template '{}' is not valid UTF-8, falling back to GB18030: {}", fileName, source);
                return decodeStrict(bytes, 0, bytes.length, GB18030);
            } catch (IllegalArgumentException gbError) {
                logger.warn("Prompt template '{}' failed strict decoding with UTF-8 and GB18030, using lenient UTF-8: {}", fileName, source);
                return decodeLenient(bytes, StandardCharsets.UTF_8);
            }
        }
    }

    private String decodeStrict(byte[] bytes, int offset, int length, Charset charset) {
        try {
            CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
            return decoder.decode(ByteBuffer.wrap(bytes, offset, length)).toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("Template decoding failed with charset: " + charset.name(), e);
        }
    }

    private String decodeLenient(byte[] bytes, Charset charset) {
        CharsetDecoder decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE);
        try {
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, charset);
        }
    }
}

