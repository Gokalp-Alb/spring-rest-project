package com.springrest.springrestproject.service.interfaces;

import com.springrest.springrestproject.dto.request.table.TableCreateRequest;
import com.springrest.springrestproject.dto.response.table.TableResponse;
import com.springrest.springrestproject.model.TableMetadata;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public interface IMetadataService {
    TableMetadata createTable(TableCreateRequest request, Long userId);
    Page<TableResponse> getAllTables(Pageable pageable);
    void deleteTableByName(String tableName, Long userId);
}