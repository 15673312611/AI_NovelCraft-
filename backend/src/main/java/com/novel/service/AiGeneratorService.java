package com.novel.service;

import com.novel.entity.AiGenerator;
import com.novel.mapper.AiGeneratorMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI Generator Service
 * Service for AI generators
 */
@Service
@Slf4j
public class AiGeneratorService {

    @Autowired
    private AiGeneratorMapper aiGeneratorMapper;

    /**
     * List active generators
     */
    public List<AiGenerator> getAllActiveGenerators() {
        log.info("List active generators");
        return aiGeneratorMapper.findAllActive();
    }

    /**
     * Get generator by ID
     */
    public AiGenerator getGeneratorById(Long id) {
        log.info("Get generator by ID: {}", id);
        AiGenerator generator = aiGeneratorMapper.findById(id);
        if (generator == null) {
            log.warn("Generator not found, id: {}", id);
            throw new RuntimeException("Generator not found");
        }
        return generator;
    }




}


