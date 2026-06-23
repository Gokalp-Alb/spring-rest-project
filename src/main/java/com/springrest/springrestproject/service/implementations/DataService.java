package com.springrest.springrestproject.service.implementations;

import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.dto.request.data.TableInsertRequest;
import com.springrest.springrestproject.dto.request.query.QueryRequest;
import com.springrest.springrestproject.dto.response.data.DataResponse;
import com.springrest.springrestproject.model.TableMetadata;
import com.springrest.springrestproject.repository.ITableMetadataRepo;
import com.springrest.springrestproject.repository.IUserRepo;
import com.springrest.springrestproject.service.implementations.Kafka.OutboundKafkaPublisher;
import com.springrest.springrestproject.service.interfaces.IDataService;
import com.springrest.springrestproject.service.interfaces.IMetadataService;
import com.springrest.springrestproject.util.DataEvaluationHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DataService implements IDataService {

    private final JdbcTemplate jdbcTemplate;
    private final ITableMetadataRepo tableMetadataRepo;
    private final IUserRepo userRepo;
    private final IMetadataService metadataService;
    private final DataEvaluationHelper dataHelper;
    private final OutboundKafkaPublisher kafkaPublisher;

    @Override
    @Transactional
    public DataResponse insertRow(TableInsertRequest request, Long userId) {
        TableMetadata metadata = tableMetadataRepo.findByTableName(request.tableName())
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        dataHelper.validateRowRegex(metadata, request.rowData());

        if (metadata.getTableContext() != null) {
            metadata.getTableContext().setLastUpdaterId(userId);
        }

        List<String> columns = new ArrayList<>(request.rowData().keySet());
        List<Object> values = new ArrayList<>(request.rowData().values());

        String columnsSql = String.join(", ", columns);

        String placeholdersSql = columns.stream()
                .map(col -> "?")
                .collect(Collectors.joining(", "));

        String insertSql = String.format("INSERT INTO %s (%s) VALUES (%s) RETURNING id;",
                request.tableName(), columnsSql, placeholdersSql);

        String logSql = dataHelper.rebuildFullSql(insertSql, values);

        metadataService.logSchemaChange(request.tableName(), logSql, userId);
        Long id = jdbcTemplate.queryForObject(insertSql, Long.class, values.toArray());
        kafkaPublisher.publishMutation(request.tableName(), "INSERT", request.rowData(), userId);

        return new DataResponse(
                id,
                request.tableName(),
                "INSERT",
                request.rowData()
        );
    }

    @Override
    public List<Map<String, Object>> executeSelect(QueryRequest request, Long userId, Pageable pageable) {
        userRepo.findById(userId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.BAD_REQUEST));
        String fieldsStr = (request.fields() == null || request.fields().isEmpty())
                ? "*"
                : String.join(", ", request.fields());

        StringBuilder sqlBuilder = new StringBuilder(String.format("SELECT %s FROM %s", fieldsStr, request.tableName()));
        List<Object> queryParams = new ArrayList<>();

        if (request.conditions() != null && !request.conditions().isEmpty()) {
            sqlBuilder.append(" WHERE ");
            for (int i = 0; i < request.conditions().size(); i++) {
                QueryRequest.Condition condition = request.conditions().get(i);
                if (i > 0) {
                    sqlBuilder.append(" AND ");
                }
                sqlBuilder.append(String.format("%s %s ?", condition.column(), condition.operator().getValue()));
                queryParams.add(condition.value());
            }
        }

        if (request.sorts() != null && !request.sorts().isEmpty()) {
            sqlBuilder.append(" ORDER BY ");
            for (int i = 0; i < request.sorts().size(); i++) {
                QueryRequest.Sort sort = request.sorts().get(i);
                String dir = ("DESC".equalsIgnoreCase(sort.direction())) ? "DESC" : "ASC";
                if (i > 0) {
                    sqlBuilder.append(", ");
                }
                sqlBuilder.append(String.format("%s %s", sort.column(), dir));
            }
        }

            sqlBuilder.append(" LIMIT ? OFFSET ?");
            queryParams.add(pageable.getPageSize());
            queryParams.add(pageable.getOffset());

            return jdbcTemplate.queryForList(sqlBuilder.toString(), queryParams.toArray());
        }


    @Override
    @Transactional(readOnly = true)
    public Page<Map<String, Object>> getTableData(String tableName, Boolean showSensitive, Pageable pageable, Long userId) {
        TableMetadata metadata = tableMetadataRepo.findByTableName(tableName)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));

        String projection = dataHelper.getProjectionClause(metadata, showSensitive);
        String countSql = String.format("SELECT COUNT(*) FROM %s", tableName);
        Long totalElements = jdbcTemplate.queryForObject(countSql, Long.class);
        if (totalElements == null) totalElements = 0L;

        String dataSql = String.format("SELECT %s FROM %s LIMIT ? OFFSET ?", projection, tableName);
        List<Map<String, Object>> content = jdbcTemplate.queryForList(dataSql, pageable.getPageSize(), pageable.getOffset());
        return new PageImpl<>(content, pageable, totalElements);
    }



    @Override
    @Transactional
    public DataResponse deleteRowById(String tableName, Long id, Long userId) {
        TableMetadata metadata = tableMetadataRepo.findByTableName(tableName)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        String deleteSql = String.format("DELETE FROM %s WHERE id = ?;", tableName);

        List<Object> logValues = new ArrayList<>();
        logValues.add(id);
        String fullSqlForLog = dataHelper.rebuildFullSql(deleteSql, logValues);
        metadataService.logSchemaChange(tableName, fullSqlForLog, userId);

        int rowsAffected = jdbcTemplate.update(deleteSql, id);
        kafkaPublisher.publishMutation(tableName, "DELETE", Map.of("id", id), userId);
        if (rowsAffected == 0) {
            throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (metadata.getTableContext() != null) {
            metadata.getTableContext().setLastUpdaterId(userId);
        }
        return new DataResponse(
                id,
                tableName,
                "DELETE",
                null
        );
    }

    @Override
    @Transactional
    public DataResponse updateRowById(String tableName, Long id, Map<String, Object> updateData, Long userId) {
        TableMetadata metadata = tableMetadataRepo.findByTableName(tableName)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        if (updateData == null || updateData.isEmpty()) {
            throw new ApplicationException(ErrorCode.BAD_REQUEST);
        }
        List<String> sets = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (Map.Entry<String, Object> entry : updateData.entrySet()) {
            if (!entry.getKey().equalsIgnoreCase("id")) {
                sets.add(entry.getKey() + " = ?");
                values.add(entry.getValue());
            }
        }
        String setSql = String.join(", ", sets);
        String updateSql = String.format("UPDATE %s SET %s WHERE id = ?;", tableName, setSql);
        values.add(id);

        List<Object> logValues = new ArrayList<>();
        logValues.add(id);
        String fullSqlForLog = dataHelper.rebuildFullSql(updateSql, logValues);
        metadataService.logSchemaChange(tableName, fullSqlForLog, userId);

        int rowsAffected = jdbcTemplate.update(updateSql, values.toArray());
        Map<String, Object> fullPayload = new HashMap<>(updateData);
        fullPayload.put("id", id);
        kafkaPublisher.publishMutation(tableName, "UPDATE", fullPayload, userId);

        if (rowsAffected == 0) {
            throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (metadata.getTableContext() != null) {
            metadata.getTableContext().setLastUpdaterId(userId);
        }

        return new DataResponse(
                id,
                tableName,
                "UPDATE",
                updateData
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> findRowById(String tableName, Long id, Boolean showSensitive, Long userId) {
        TableMetadata metadata = tableMetadataRepo.findByTableName(tableName)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));

        String projection = dataHelper.getProjectionClause(metadata, showSensitive);
        String dataSql = String.format("SELECT %s FROM %s WHERE id = ?;", projection, tableName);
        List<Map<String, Object>> records = jdbcTemplate.queryForList(dataSql, id);
        if (records.isEmpty()) {
            throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return records.getFirst();
    }

}