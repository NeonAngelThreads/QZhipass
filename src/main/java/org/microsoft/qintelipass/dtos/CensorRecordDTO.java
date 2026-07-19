package org.microsoft.qintelipass.dtos;

import org.microsoft.qintelipass.entity.CensorRecord;

import java.time.LocalDateTime;

public record CensorRecordDTO(
        Long id,
        Long userId,
        String username,
        String phone,
        String department,
        String modelName,
        String hitKeywords,
        LocalDateTime createdAt
) {
    public static CensorRecordDTO from(CensorRecord record) {
        return new CensorRecordDTO(
                record.getId(),
                record.getUserId(),
                record.getUsername(),
                record.getPhone(),
                record.getDepartment(),
                record.getModelName(),
                record.getHitKeywords(),
                record.getCreatedAt()
        );
    }
}