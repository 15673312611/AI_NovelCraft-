package com.novel.controller;

import com.novel.entity.AiGenerator;
import com.novel.service.AiGeneratorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI Generator Controller
 * API endpoints cho AI Generator
 */
@RestController
@RequestMapping("/ai-generator")
@CrossOrigin(origins = "*")
@Slf4j
public class AiGeneratorController {

    @Autowired
    private AiGeneratorService aiGeneratorService;

    /**
     * Lấy tất cả generator đang active
     * GET /api/ai-generator
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllActiveGenerators() {
        log.info("API: Lấy tất cả generator đang active");
        try {
            List<AiGenerator> generators = aiGeneratorService.getAllActiveGenerators();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", generators);
            response.put("message", "Lấy danh sách generator thành công");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Lỗi khi lấy danh sách generator: ", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Lỗi: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Lấy generator theo category
     * GET /api/ai-generator/category/{category}
     */
    @GetMapping("/category/{category}")
    public ResponseEntity<Map<String, Object>> getGeneratorsByCategory(@PathVariable String category) {
        log.info("API: Lấy generator theo category: {}", category);
        try {
            List<AiGenerator> generators = aiGeneratorService.getGeneratorsByCategory(category);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", generators);
            response.put("message", "Lấy danh sách generator thành công");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Lỗi khi lấy generator theo category: ", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Lỗi: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Lấy generator theo ID
     * GET /api/ai-generator/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getGeneratorById(@PathVariable Long id) {
        log.info("API: Lấy generator theo ID: {}", id);
        try {
            AiGenerator generator = aiGeneratorService.getGeneratorById(id);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", generator);
            response.put("message", "Lấy generator thành công");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Lỗi khi lấy generator: ", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Lỗi: " + e.getMessage());
            return ResponseEntity.status(404).body(errorResponse);
        }
    }

    /**
     * Tạo generator mới
     * POST /api/ai-generator
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createGenerator(@RequestBody AiGenerator generator) {
        log.info("API: Tạo generator mới: {}", generator.getName());
        try {
            AiGenerator created = aiGeneratorService.createGenerator(generator);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", created);
            response.put("message", "Tạo generator thành công");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Lỗi khi tạo generator: ", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Lỗi: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Cập nhật generator
     * PUT /api/ai-generator/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateGenerator(
            @PathVariable Long id,
            @RequestBody AiGenerator generator) {
        log.info("API: Cập nhật generator ID: {}", id);
        try {
            generator.setId(id);
            AiGenerator updated = aiGeneratorService.updateGenerator(generator);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", updated);
            response.put("message", "Cập nhật generator thành công");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Lỗi khi cập nhật generator: ", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Lỗi: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Xóa generator
     * DELETE /api/ai-generator/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteGenerator(@PathVariable Long id) {
        log.info("API: Xóa generator ID: {}", id);
        try {
            aiGeneratorService.deleteGenerator(id);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Xóa generator thành công");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Lỗi khi xóa generator: ", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Lỗi: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Lấy tất cả generator (bao gồm inactive) - dành cho admin
     * GET /api/ai-generator/admin/all
     */
    @GetMapping("/admin/all")
    public ResponseEntity<Map<String, Object>> getAllGenerators() {
        log.info("API: Lấy tất cả generator (admin)");
        try {
            List<AiGenerator> generators = aiGeneratorService.getAllGenerators();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", generators);
            response.put("message", "Lấy danh sách generator thành công");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Lỗi khi lấy tất cả generator: ", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Lỗi: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}

