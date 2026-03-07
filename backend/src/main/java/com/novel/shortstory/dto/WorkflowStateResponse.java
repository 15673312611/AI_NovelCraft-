package com.novel.shortstory.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WorkflowStateResponse {
    private Long novelId;
    private String status;
    private String activeStep;
    private Integer chapterCount;
    private Integer currentChapter;
    private List<WorkflowStep> steps = new ArrayList<>();

    @Data
    public static class WorkflowStep {
        private String key;
        private String name;
        /**
         * PENDING / RUNNING / COMPLETED / FAILED
         */
        private String status;
        private Integer chapterNumber;
        private String description;

        public WorkflowStep() {}

        public WorkflowStep(String key, String name, String status, Integer chapterNumber, String description) {
            this.key = key;
            this.name = name;
            this.status = status;
            this.chapterNumber = chapterNumber;
            this.description = description;
        }
    }
}
