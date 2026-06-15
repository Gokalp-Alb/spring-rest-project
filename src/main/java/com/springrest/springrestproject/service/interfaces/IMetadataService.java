package com.springrest.springrestproject.service.interfaces;

import com.springrest.springrestproject.dto.request.table.TableCreateRequest;
import com.springrest.springrestproject.model.TableMetadata;

import java.util.List;

public interface IMetadataService {
    TableMetadata createTable(TableCreateRequest request, Long userId);
    List<TableMetadata> getAllTables(Long userId);
    void deleteTableByName(String tableName, Long userId);
}