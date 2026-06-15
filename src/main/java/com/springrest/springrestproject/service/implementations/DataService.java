package com.springrest.springrestproject.service.implementations;

import com.springrest.springrestproject.dto.request.data.TableInsertRequest;
import com.springrest.springrestproject.repository.ITableMetadataRepo;
import com.springrest.springrestproject.service.interfaces.IDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DataService implements IDataService {

    private final JdbcTemplate jdbcTemplate;
    private final ITableMetadataRepo tableMetadataRepo;

    @Override
    @Transactional
    public void insertRow(TableInsertRequest request, Long userId) {
        tableMetadataRepo.findByTableName(request.tableName())
                .orElseThrow(() -> new RuntimeException("Table metadata not found for: " + request.tableName()));

        List<String> columns = new ArrayList<>(request.rowData().keySet());
        List<Object> values = new ArrayList<>(request.rowData().values());

        String columnsSql = String.join(", ", columns);

        String placeholdersSql = columns.stream()
                .map(col -> "?")
                .collect(Collectors.joining(", "));

        String insertSql = String.format("INSERT INTO %s (%s) VALUES (%s);",
                request.tableName(), columnsSql, placeholdersSql);

        jdbcTemplate.update(insertSql, values.toArray());
    }
}