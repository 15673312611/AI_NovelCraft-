package com.novel.service;

import com.novel.entity.ReferenceFile;
import com.novel.mapper.ReferenceFileMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 参考文件服务
 */
@Service
@Slf4j
public class ReferenceFileService {

    @Autowired
    private ReferenceFileMapper referenceFileMapper;

    @Autowired
    private FileParserService fileParserService;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    /**
     * 获取小说的所有参考文件
     */
    public List<ReferenceFile> getReferenceFilesByNovelId(Long novelId) {
        log.info("获取小说ID={}的参考文件", novelId);
        return referenceFileMapper.findByNovelId(novelId);
    }

    /**
     * 根据ID获取参考文件
     */
    public ReferenceFile getReferenceFileById(Long id) {
        log.info("获取参考文件ID={}", id);
        return referenceFileMapper.findById(id);
    }

    /**
     * 上传参考文件
     */
    @Transactional
    public ReferenceFile uploadReferenceFile(Long novelId, MultipartFile file) throws Exception {
        log.info("上传参考文件: {}", file.getOriginalFilename());
        
        // 验证文件大小
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new Exception("文件大小超过限制（最大10MB）");
        }
        
        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new Exception("文件名不能为空");
        }
        
        // 获取文件类型
        String fileType = getFileExtension(fileName);
        if (!fileType.equals("txt") && !fileType.equals("docx")) {
            throw new Exception("只支持txt和docx文件");
        }
        
        // 解析文件内容
        InputStream inputStream = file.getInputStream();
        String content = fileParserService.parseFile(inputStream, fileType);
        inputStream.close();
        
        // 计算字数
        int wordCount = fileParserService.countWords(content);
        
        // 创建记录
        ReferenceFile referenceFile = new ReferenceFile();
        referenceFile.setNovelId(novelId);
        referenceFile.setFileName(fileName);
        referenceFile.setFileType(fileType);
        referenceFile.setFileContent(content);
        referenceFile.setFileSize(file.getSize());
        referenceFile.setWordCount(wordCount);
        // 注：实际文件存储路径可以根据需要实现
        referenceFile.setOriginalPath(null);
        
        int result = referenceFileMapper.insert(referenceFile);
        if (result > 0) {
            log.info("参考文件上传成功，ID={}", referenceFile.getId());
            return referenceFile;
        }
        throw new Exception("参考文件上传失败");
    }

    /**
     * 删除参考文件
     */
    @Transactional
    public void deleteReferenceFile(Long id) {
        log.info("删除参考文件ID={}", id);
        int result = referenceFileMapper.delete(id);
        if (result == 0) {
            throw new RuntimeException("参考文件删除失败");
        }
    }

    /**
     * 根据ID列表批量获取参考文件
     */
    public List<ReferenceFile> getReferenceFilesByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        log.info("批量获取参考文件，数量={}", ids.size());
        return referenceFileMapper.findByIds(ids);
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1).toLowerCase();
        }
        return "";
    }
}

