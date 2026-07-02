package com.springrest.springrestproject.service.interfaces;

import com.springrest.springrestproject.dto.request.data.TableInsertRequest;
import com.springrest.springrestproject.dto.request.query.QueryRequest;
import com.springrest.springrestproject.dto.response.data.DataResponse;
import com.springrest.springrestproject.dto.response.data.QueryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;

public interface IDataService{
    DataResponse insertRow(TableInsertRequest request, Long userId);
    QueryResponse executeSelect(QueryRequest request, Long userId, Pageable pageable);
    Page<Map<String, Object>> getTableData(String tableName, Boolean showSensitive, Pageable pageable, Long userId);
    DataResponse deleteRowById(String tableName, Long id, Long userId);
    DataResponse updateRowById(String tableName, Long id, Map<String, Object> updateData, Long userId);
    Map<String, Object> findRowById(String tableName, Long id, Boolean showSensitive, Long userId);
}