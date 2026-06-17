package com.springrest.springrestproject.service.implementations;

import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.dto.request.data.TableInsertRequest;
import com.springrest.springrestproject.dto.request.query.SelectQueryRequest;
import com.springrest.springrestproject.model.ColumnMetadata;
import com.springrest.springrestproject.model.TableMetadata;
import com.springrest.springrestproject.repository.ITableMetadataRepo;
import com.springrest.springrestproject.repository.IUserRepo;
import com.springrest.springrestproject.service.interfaces.IDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DataService implements IDataService {

    private final JdbcTemplate jdbcTemplate;
    private final ITableMetadataRepo tableMetadataRepo;
    private final IUserRepo userRepo;

    @Override
    @Transactional
    public void insertRow(TableInsertRequest request, Long userId) {
        TableMetadata metadata = tableMetadataRepo.findByTableName(request.tableName())
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));

        if (metadata.getAdminContext() != null) {
            metadata.getAdminContext().setLastUpdaterId(userId);
        }

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

    @Override
    public List<Map<String, Object>> executeSelect(SelectQueryRequest request, Long userId, Pageable pageable) {
        userRepo.findById(userId).orElseThrow(() -> new RuntimeException("User unrecognized"));

        String fieldsStr = (request.fields() == null || request.fields().isEmpty())
                ? "*"
                : String.join(", ", request.fields());

        StringBuilder sqlBuilder = new StringBuilder(String.format("SELECT %s FROM %s", fieldsStr, request.tableName()));

        if (request.whereColumn() != null && !request.whereColumn().isEmpty()) {
            sqlBuilder.append(String.format(" WHERE %s = ?", request.whereColumn()));
            sqlBuilder.append(" LIMIT ? OFFSET ?");
            return jdbcTemplate.queryForList(
                    sqlBuilder.toString(),
                    request.whereValue(),
                    pageable.getPageSize(),
                    pageable.getOffset()
            );
        }

        sqlBuilder.append(" LIMIT ? OFFSET ?");
        return jdbcTemplate.queryForList(
                sqlBuilder.toString(),
                pageable.getPageSize(),
                pageable.getOffset()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Map<String, Object>> getTableData(String tableName, Boolean showSensitive, Pageable pageable, Long userId) {
        TableMetadata metadata = tableMetadataRepo.findByTableName(tableName)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        boolean shouldRedact;
        if (showSensitive != null) {
            shouldRedact = !showSensitive;
        } else {
            shouldRedact = (metadata.getAdminContext() != null) && metadata.getAdminContext().isSensitive();
        }
        String projection = "*";
        if (shouldRedact) {
            List<String> columnProjections = new ArrayList<>();
            columnProjections.add("id");
            for (ColumnMetadata col : metadata.getColumns()) {
                if (!col.getColumnName().equalsIgnoreCase("id")) {
                    columnProjections.add("'********' AS " + col.getColumnName());
                }
            }
            projection = String.join(", ", columnProjections);
        }
        String countSql = String.format("SELECT COUNT(*) FROM %s", tableName);
        Long totalElements = jdbcTemplate.queryForObject(countSql, Long.class);
        if (totalElements == null) totalElements = 0L;
        String dataSql = String.format("SELECT %s FROM %s LIMIT ? OFFSET ?", projection, tableName);
        List<Map<String, Object>> content = jdbcTemplate.queryForList(dataSql, pageable.getPageSize(), pageable.getOffset());
        return new PageImpl<>(content, pageable, totalElements);
    }
}