package org.microsoft.qintelipass.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenUsageRankDTO {
    private Long userId;
    private String userName;
    private Long totalTokens;
    private Integer rank;
}
