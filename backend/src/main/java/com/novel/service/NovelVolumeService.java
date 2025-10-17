package com.novel.service;

import com.novel.domain.entity.NovelVolume;
import com.novel.mapper.NovelVolumeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 小说卷服务层
 * 负责卷的基础CRUD操作
 * 
 * @author Novel Creation System
 * @version 1.0.0
 * @since 2024-01-01
 */
@Service
public class NovelVolumeService {

    @Autowired
    private NovelVolumeMapper volumeMapper;

    /**
     * 手动修改卷信息
     */
    @Transactional
    public NovelVolume updateVolume(Long volumeId, String title, String theme, String description, String contentOutline) {
        NovelVolume volume = volumeMapper.selectById(volumeId);
        if (volume == null) {
            throw new RuntimeException("卷不存在: " + volumeId);
        }

        volume.setTitle(title);
        volume.setTheme(theme);
        volume.setDescription(description);
        volume.setContentOutline(contentOutline);

        volumeMapper.updateById(volume);

        return volume;
    }

    /**
     * 根据小说ID获取所有卷
     * 前端调用：NovelVolumeController.getVolumesByNovelId()
     */
    public List<NovelVolume> getVolumesByNovelId(Long novelId) {
        return volumeMapper.selectByNovelId(novelId);
    }

    /**
     * 根据ID获取卷
     * 前端调用：VolumeController 和前端多处使用
     */
    public NovelVolume getById(Long id) {
        return volumeMapper.selectById(id);
    }

    /**
     * 更新卷的状态
     */
    @Transactional
    public NovelVolume updateVolumeStatus(Long volumeId, NovelVolume.VolumeStatus status) {
        NovelVolume volume = volumeMapper.selectById(volumeId);
        if (volume == null) {
            throw new RuntimeException("卷不存在: " + volumeId);
        }

        volume.setStatus(status);
        volumeMapper.updateById(volume);

        return volume;
    }
}
