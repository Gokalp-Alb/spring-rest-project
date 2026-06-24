package com.springrest.springrestproject.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemDdlLog {
    private Long id;
    private String tableName;
    private String executedSql;
    private Long userId;
    private LocalDateTime executedAt;
}