package com.springrest.springrestproject.service.implementations;

import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.core.mapper.TableMapper;
import com.springrest.springrestproject.dto.request.table.TableCreateRequest;
import com.springrest.springrestproject.dto.response.table.TableResponse;
import com.springrest.springrestproject.model.AdminSecurityContext;
import com.springrest.springrestproject.model.SystemDdlLog;
import com.springrest.springrestproject.model.TableMetadata;
import com.springrest.springrestproject.repository.ISystemDdlLogRepo;
import com.springrest.springrestproject.repository.ITableMetadataRepo;
import com.springrest.springrestproject.service.interfaces.IMetadataService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MetadataService implements IMetadataService {

    private final ITableMetadataRepo tableMetadataRepo;
    private final ISystemDdlLogRepo ddlLogRepo;
    private final JdbcTemplate jdbcTemplate;
    private final TableMapper tableMapper;

    @Override
    @Transactional
    public TableMetadata createTable(String tableName, TableCreateRequest request, Long userId) {
        String columnsSql = request.columns().stream()
                .map(col -> col.getColumnName() + " " + col.getDataType())
                .collect(Collectors.joining(", "));
        String createTableSql = String.format("CREATE TABLE IF NOT EXISTS %s (id SERIAL PRIMARY KEY, %s);",
                tableName, columnsSql);
        logSchemaChange(tableName, createTableSql, userId);
        jdbcTemplate.execute(createTableSql);

        AdminSecurityContext securityContext = new AdminSecurityContext();
        securityContext.setCreatorId(userId);
        securityContext.setLastUpdaterId(userId);
        securityContext.setIsSensitive(request.isSensitive() != null && request.isSensitive());

        TableMetadata metadata = new TableMetadata();
        metadata.setTableName(tableName);
        metadata.setColumns(request.columns());
        metadata.setAdminContext(securityContext);

        return tableMetadataRepo.save(metadata);
    }

    @Override
    public Page<TableResponse> getAllTables(Pageable pageable) {
        Page<TableMetadata> metadataPage = tableMetadataRepo.findAll(pageable);
        return metadataPage.map(tableMapper::toResponse);
    }

    @Override
    public TableResponse getTableById(Long tableId) {
        TableMetadata metadata = tableMetadataRepo.findById(tableId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        return tableMapper.toResponse(metadata);
    }

    @Override
    @Transactional
    public void deleteTableByName(String tableName, Long userId) {
        TableMetadata metadata = tableMetadataRepo.findByTableName(tableName)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        String dropTableSql = String.format("DROP TABLE IF EXISTS %s;", tableName);
        logSchemaChange(tableName, dropTableSql, userId);
        jdbcTemplate.execute(dropTableSql);
        tableMetadataRepo.delete(metadata);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSchemaChange(String tableName, String sql, Long userId) {
        SystemDdlLog logEntry = SystemDdlLog.builder()
                .tableName(tableName)
                .executedSql(sql)
                .userId(userId)
                .executedAt(LocalDateTime.now())
                .build();

        ddlLogRepo.save(logEntry);
    }

}