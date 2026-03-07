package com.novel.script.repository;

import com.novel.script.entity.VideoScriptLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VideoScriptLogRepository extends JpaRepository<VideoScriptLog, Long> {
    List<VideoScriptLog> findByScriptIdOrderByCreatedAtDesc(Long scriptId, Pageable pageable);

    List<VideoScriptLog> findByScriptIdAndEpisodeNumberOrderByCreatedAtDesc(Long scriptId, Integer episodeNumber, Pageable pageable);

    void deleteByScriptId(Long scriptId);
}
