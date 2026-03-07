package com.novel.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.admin.dto.OperationLogDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminLogService {

    public Page<OperationLogDTO> getLogs(String username, String startDate, String endDate, int page, int size) {
        // 暂时返回空数据，后续可以实现真实的日志记录
        Page<OperationLogDTO> pageResult = new Page<>(page, size);
        pageResult.setRecords(new ArrayList<>());
        pageResult.setTotal(0);
        return pageResult;
    }

    public Map<String, Object> getLogStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("todayCount", 0);
        stats.put("activeUsers", 0);
        return stats;
    }
}
