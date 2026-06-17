package com.springrest.springrestproject.repository;

import com.springrest.springrestproject.model.TableMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ITableMetadataRepo extends JpaRepository<TableMetadata, Long> {
    Optional<TableMetadata> findByTableName(String tableName);
}
