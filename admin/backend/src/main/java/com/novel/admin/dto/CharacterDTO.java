package com.novel.admin.dto;

import lombok.Data;

@Data
public class CharacterDTO {
    private Long id;
    private Long novelId;
    private String name;
    private String characterType;
    private String status;
    private Integer appearanceCount;
    private String description;
}
