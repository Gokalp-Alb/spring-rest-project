package com.springrest.springrestproject.service.interfaces;

import com.springrest.springrestproject.dto.request.table.TableCreateRequest;
import com.springrest.springrestproject.dto.response.table.TableResponse;
import com.springrest.springrestproject.model.table.TableMetadata;
import com.springrest.springrestproject.service.interfaces.ReadServices.IMetadataReadService;


public interface IMetadataService extends IMetadataReadService {
    TableMetadata createTable(String tablename, TableCreateRequest request, Long userId);
    TableResponse deleteTableByName(String tableName, Long userId);
    void logSchemaChange(String tableName, String sql, Long userId);
}