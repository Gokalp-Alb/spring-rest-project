package com.springrest.springrestproject.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;

@Entity
@Table(name = "table_metadata")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class TableMetadata {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String tableName;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "table_id")
    private List<ColumnMetadata> columns;

    @Embedded
    private TableContext tableContext;
}