package com.novel.shortstory.repository;

import com.novel.shortstory.entity.ShortChapter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShortChapterRepository extends JpaRepository<ShortChapter, Long> {
    List<ShortChapter> findByNovelIdOrderByChapterNumberAsc(Long novelId);
    Optional<ShortChapter> findByNovelIdAndChapterNumber(Long novelId, Integer chapterNumber);
    void deleteByNovelId(Long novelId);
}
