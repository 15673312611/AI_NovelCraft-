package com.novel.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.admin.dto.OperationLogDTO;
import com.novel.admin.service.AdminLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/logs")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AdminLogController {

    private final AdminLogService logService;

    @GetMapping
    public Page<OperationLogDTO> getLogs(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return logService.getLogs(username, startDate, endDate, page, size);
    }

    @GetMapping("/stats")
    public Object getLogStats() {
        return logService.getLogStats();
    }
}
