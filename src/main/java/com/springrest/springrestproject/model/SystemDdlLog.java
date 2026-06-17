package com.springrest.springrestproject.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "system_ddl_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemDdlLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "table_name", nullable = false)
    private String tableName;

    @Column(name = "executed_sql", nullable = false, columnDefinition = "TEXT")
    private String executedSql;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;
}