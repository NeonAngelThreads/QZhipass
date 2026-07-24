package org.microsoft.qintelipass.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserTokenUsageDTO {
    private Long userId;
    private String userName;
    private Long tokenUsed;
    private Long tokenLimit;
    private boolean isExceeded;
}
