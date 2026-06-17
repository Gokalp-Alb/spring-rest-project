package com.springrest.springrestproject.service.interfaces;

import com.springrest.springrestproject.dto.request.data.TableInsertRequest;
import com.springrest.springrestproject.dto.request.query.SelectQueryRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

public interface IDataService{
    void insertRow(TableInsertRequest request, Long userId);
    List<Map<String, Object>> executeSelect(SelectQueryRequest request, Long userId, Pageable pageable);
    Page<Map<String, Object>> getTableData(String tableName, Boolean showSensitive, Pageable pageable, Long userId);
}