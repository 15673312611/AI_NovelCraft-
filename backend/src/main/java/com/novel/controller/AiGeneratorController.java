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
     * List active generators
     * GET /api/ai-generator
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllActiveGenerators() {
        log.info("API: list active generators");
        try {
            List<AiGenerator> generators = aiGeneratorService.getAllActiveGenerators();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", generators);
            response.put("message", "Fetched generators successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to fetch generators", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Get generator by ID
     * GET /api/ai-generator/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getGeneratorById(@PathVariable Long id) {
        log.info("API: get generator by ID: {}", id);
        try {
            AiGenerator generator = aiGeneratorService.getGeneratorById(id);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", generator);
            response.put("message", "Fetched generator successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to fetch generator", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(404).body(errorResponse);
        }
    }




}


