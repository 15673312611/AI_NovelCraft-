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

@Service
@Slf4j
public class ReferenceFileService {

    @Autowired
    private ReferenceFileMapper referenceFileMapper;

    @Autowired
    private FileParserService fileParserService;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    public List<ReferenceFile> getReferenceFilesByNovelId(Long novelId) {
        log.info("Get reference files for novel={}", novelId);
        return referenceFileMapper.findByNovelId(novelId);
    }

    public ReferenceFile getReferenceFileById(Long id) {
        log.info("Get reference file={}", id);
        return referenceFileMapper.findById(id);
    }

    public ReferenceFile getReferenceFileByNovelId(Long novelId, Long id) {
        ReferenceFile referenceFile = getReferenceFileById(id);
        if (referenceFile == null) {
            throw new RuntimeException("参考文件不存在");
        }
        if (referenceFile.getNovelId() == null || !referenceFile.getNovelId().equals(novelId)) {
            throw new RuntimeException("参考文件不属于当前小说");
        }
        return referenceFile;
    }

    @Transactional
    public ReferenceFile uploadReferenceFile(Long novelId, MultipartFile file) throws Exception {
        log.info("Upload reference file {}", file.getOriginalFilename());

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new Exception("文件大小超过限制（最大 10MB）");
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new Exception("文件名不能为空");
        }

        String fileType = getFileExtension(fileName);
        if (!fileType.equals("txt") && !fileType.equals("docx")) {
            throw new Exception("只支持 txt 和 docx 文件");
        }

        InputStream inputStream = file.getInputStream();
        String content = fileParserService.parseFile(inputStream, fileType);
        inputStream.close();

        int wordCount = fileParserService.countWords(content);

        ReferenceFile referenceFile = new ReferenceFile();
        referenceFile.setNovelId(novelId);
        referenceFile.setFileName(fileName);
        referenceFile.setFileType(fileType);
        referenceFile.setFileContent(content);
        referenceFile.setFileSize(file.getSize());
        referenceFile.setWordCount(wordCount);
        referenceFile.setOriginalPath(null);

        int result = referenceFileMapper.insert(referenceFile);
        if (result > 0) {
            log.info("Reference file uploaded, id={}", referenceFile.getId());
            return referenceFile;
        }
        throw new Exception("参考文件上传失败");
    }

    @Transactional
    public void deleteReferenceFile(Long id) {
        log.info("Delete reference file={}", id);
        int result = referenceFileMapper.delete(id);
        if (result == 0) {
            throw new RuntimeException("参考文件删除失败");
        }
    }

    public List<ReferenceFile> getReferenceFilesByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        log.info("Get reference files by ids, count={}", ids.size());
        return referenceFileMapper.findByIds(ids);
    }

    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1).toLowerCase();
        }
        return "";
    }
}
