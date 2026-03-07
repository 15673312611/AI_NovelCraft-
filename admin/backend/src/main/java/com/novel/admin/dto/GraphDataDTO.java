package com.novel.admin.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class GraphDataDTO {
    private List<CharacterStateDTO> characterStates;
    private List<RelationshipStateDTO> relationshipStates;
    private List<OpenQuestDTO> openQuests;
    private List<EventDTO> events;
    private Integer totalCharacterStates;
    private Integer totalRelationshipStates;
    private Integer totalOpenQuests;
    private Integer totalEvents;
    
    @Data
    public static class CharacterStateDTO {
        private String name;
        private String location;
        private String realm;
        private String characterInfo;
        private Boolean alive;
        private Integer chapter;
    }
    
    @Data
    public static class RelationshipStateDTO {
        private String a;
        private String b;
        private String type;
        private Double strength;
        private Integer chapter;
    }
    
    @Data
    public static class OpenQuestDTO {
        private String id;
        private String description;
        private String status;
        private Integer introduced;
        private Integer due;
        private Integer lastUpdated;
    }
    
    @Data
    public static class EventDTO {
        private Integer chapter;
        private String summary;
        private String location;
        private List<String> participants;
        private Double importance;
        private String emotionalTone;
        private List<String> tags;
    }
}
