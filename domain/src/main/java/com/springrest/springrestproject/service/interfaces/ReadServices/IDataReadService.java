package com.springrest.springrestproject.service.interfaces.ReadServices;

import com.springrest.springrestproject.dto.request.query.QueryRequest;
import com.springrest.springrestproject.dto.response.data.QueryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;

public interface IDataReadService {
    QueryResponse executeSelect(QueryRequest request, Long userId, Pageable pageable);
    Page<Map<String, Object>> getTableData(String tableName, Boolean showSensitive, Pageable pageable, Long userId);
    Map<String, Object> findRowById(String tableName, Long id, Boolean showSensitive, Long userId);
}
