package com.novel.service;

import com.novel.entity.AiGenerator;
import com.novel.mapper.AiGeneratorMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI Generator Service
 * Quản lý các generator và prompt template
 */
@Service
@Slf4j
public class AiGeneratorService {

    @Autowired
    private AiGeneratorMapper aiGeneratorMapper;

    /**
     * Lấy tất cả generator đang active
     */
    public List<AiGenerator> getAllActiveGenerators() {
        log.info("Lấy tất cả generator đang active");
        return aiGeneratorMapper.findAllActive();
    }

    /**
     * Lấy generator theo category
     */
    public List<AiGenerator> getGeneratorsByCategory(String category) {
        log.info("Lấy generator theo category: {}", category);
        return aiGeneratorMapper.findByCategory(category);
    }

    /**
     * Lấy generator theo ID
     */
    public AiGenerator getGeneratorById(Long id) {
        log.info("Lấy generator theo ID: {}", id);
        AiGenerator generator = aiGeneratorMapper.findById(id);
        if (generator == null) {
            log.warn("Không tìm thấy generator với ID: {}", id);
            throw new RuntimeException("Generator không tồn tại");
        }
        return generator;
    }

    /**
     * Thêm generator mới
     */
    public AiGenerator createGenerator(AiGenerator generator) {
        log.info("Tạo generator mới: {}", generator.getName());
        
        // Set giá trị mặc định
        if (generator.getStatus() == null) {
            generator.setStatus(1);
        }
        if (generator.getSortOrder() == null) {
            generator.setSortOrder(0);
        }
        
        aiGeneratorMapper.insert(generator);
        log.info("Tạo generator thành công với ID: {}", generator.getId());
        return generator;
    }

    /**
     * Cập nhật generator
     */
    public AiGenerator updateGenerator(AiGenerator generator) {
        log.info("Cập nhật generator ID: {}", generator.getId());
        
        // Kiểm tra generator có tồn tại không
        AiGenerator existing = aiGeneratorMapper.findById(generator.getId());
        if (existing == null) {
            log.error("Không tìm thấy generator với ID: {}", generator.getId());
            throw new RuntimeException("Generator không tồn tại");
        }
        
        int updated = aiGeneratorMapper.update(generator);
        if (updated > 0) {
            log.info("Cập nhật generator thành công");
            return aiGeneratorMapper.findById(generator.getId());
        } else {
            log.error("Cập nhật generator thất bại");
            throw new RuntimeException("Cập nhật generator thất bại");
        }
    }

    /**
     * Xóa generator (soft delete)
     */
    public void deleteGenerator(Long id) {
        log.info("Xóa generator ID: {}", id);
        
        AiGenerator existing = aiGeneratorMapper.findById(id);
        if (existing == null) {
            log.error("Không tìm thấy generator với ID: {}", id);
            throw new RuntimeException("Generator không tồn tại");
        }
        
        int deleted = aiGeneratorMapper.delete(id);
        if (deleted > 0) {
            log.info("Xóa generator thành công");
        } else {
            log.error("Xóa generator thất bại");
            throw new RuntimeException("Xóa generator thất bại");
        }
    }

    /**
     * Lấy tất cả generator (bao gồm inactive) - dành cho admin
     */
    public List<AiGenerator> getAllGenerators() {
        log.info("Lấy tất cả generator");
        return aiGeneratorMapper.findAll();
    }
}

