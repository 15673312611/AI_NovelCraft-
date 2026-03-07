package com.novel.script.repository;

import com.novel.script.entity.VideoScriptEpisode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VideoScriptEpisodeRepository extends JpaRepository<VideoScriptEpisode, Long> {
    List<VideoScriptEpisode> findByScriptIdOrderByEpisodeNumberAsc(Long scriptId);

    Optional<VideoScriptEpisode> findByScriptIdAndEpisodeNumber(Long scriptId, Integer episodeNumber);

    void deleteByScriptId(Long scriptId);
}
