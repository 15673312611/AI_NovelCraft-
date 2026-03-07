package com.novel.shortstory.repository;

import com.novel.shortstory.entity.WorkflowLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkflowLogRepository extends JpaRepository<WorkflowLog, Long> {
    List<WorkflowLog> findByNovelIdOrderByCreatedAtDesc(Long novelId, Pageable pageable);
    void deleteByNovelId(Long novelId);
}
