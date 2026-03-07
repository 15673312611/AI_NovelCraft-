package com.novel.script.repository;

import com.novel.script.entity.VideoScript;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VideoScriptRepository extends JpaRepository<VideoScript, Long> {
    List<VideoScript> findByUserIdOrderByUpdatedAtDesc(Long userId);
}
