package com.springrest.springrestproject.repository;

import com.springrest.springrestproject.model.TableMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ITableMetadataRepo extends JpaRepository<TableMetadata, Long> {
    Optional<TableMetadata> findByTableName(String tableName);
}
