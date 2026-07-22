package org.microsoft.qintelipass.dtos;

import lombok.Data;

@Data
public class CensorKeywordDTO {

    private Long id;
    private String code;
    private String keyword;
    private String category;
    private String riskLevel;
    private boolean enabled;
    private long triggerCount;
    private String createdAt;
    private String updatedAt;
}