package com.novel.shortstory.repository;

import com.novel.shortstory.entity.ShortNovel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShortNovelRepository extends JpaRepository<ShortNovel, Long> {
    List<ShortNovel> findByUserIdOrderByUpdatedAtDesc(Long userId);
}
