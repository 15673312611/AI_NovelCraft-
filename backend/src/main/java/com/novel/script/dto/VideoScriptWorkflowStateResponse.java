package com.novel.script.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class VideoScriptWorkflowStateResponse {
    private Long scriptId;
    private String status;
    private String activeStep;

    private Integer episodeCount;
    private Integer currentEpisode;

    private List<WorkflowStep> steps = new ArrayList<>();

    @Data
    public static class WorkflowStep {
        private String key;
        private String name;
        /** PENDING / RUNNING / COMPLETED / FAILED */
        private String status;
        /** 可选：第几集（循环步骤） */
        private Integer episodeNumber;
        private String description;

        public WorkflowStep() {}

        public WorkflowStep(String key, String name, String status, Integer episodeNumber, String description) {
            this.key = key;
            this.name = name;
            this.status = status;
            this.episodeNumber = episodeNumber;
            this.description = description;
        }
    }
}
