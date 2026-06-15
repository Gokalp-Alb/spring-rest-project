package com.springrest.springrestproject.service.implementations;

import com.springrest.springrestproject.dto.request.table.TableCreateRequest;
import com.springrest.springrestproject.model.AppUser;
import com.springrest.springrestproject.model.TableMetadata;
import com.springrest.springrestproject.repository.ITableMetadataRepo;
import com.springrest.springrestproject.repository.IUserRepo;
import com.springrest.springrestproject.service.interfaces.IMetadataService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MetadataService implements IMetadataService {

    private final ITableMetadataRepo tableMetadataRepo;
    private final IUserRepo userRepo;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public TableMetadata createTable(TableCreateRequest request, Long userId) {
        AppUser user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String columnsSql = request.columns().stream()
                .map(col -> col.getColumnName() + " " + col.getDataType())
                .collect(Collectors.joining(", "));

        String createTableSql = String.format("CREATE TABLE IF NOT EXISTS %s (id SERIAL PRIMARY KEY, %s);",
                request.tableName(), columnsSql);

        jdbcTemplate.execute(createTableSql);

        TableMetadata metadata = new TableMetadata();
        metadata.setTableName(request.tableName());
        metadata.setColumns(request.columns());
        return tableMetadataRepo.save(metadata);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TableMetadata> getAllTables(Long userId) {
        return tableMetadataRepo.findAll();
    }

    @Override
    @Transactional
    public void deleteTableByName(String tableName, Long userId) {
        TableMetadata metadata = tableMetadataRepo.findByTableName(tableName)
                .orElseThrow(() -> new RuntimeException("Table metadata record not found for: " + tableName));
        String dropTableSql = String.format("DROP TABLE IF EXISTS %s;", tableName);
        jdbcTemplate.execute(dropTableSql);
        tableMetadataRepo.delete(metadata);
    }

}