package com.springrest.springrestproject.service.interfaces;

import com.springrest.springrestproject.dto.request.table.TableCreateRequest;
import com.springrest.springrestproject.dto.response.table.TableResponse;
import com.springrest.springrestproject.model.table.TableMetadata;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public interface IMetadataService {
    TableMetadata createTable(String tablename, TableCreateRequest request, Long userId);
    Page<TableResponse> getAllTables(Pageable pageable);
    TableResponse getTableById(Long tableId);
    TableResponse deleteTableByName(String tableName, Long userId);
    void logSchemaChange(String tableName, String sql, Long userId);
    TableResponse getTableByName(String tableId);
}