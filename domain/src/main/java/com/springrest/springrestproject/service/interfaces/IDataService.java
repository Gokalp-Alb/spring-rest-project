package com.springrest.springrestproject.service.interfaces;

import com.springrest.springrestproject.dto.request.data.TableInsertRequest;
import com.springrest.springrestproject.dto.response.data.DataResponse;
import com.springrest.springrestproject.service.interfaces.ReadServices.IDataReadService;

import java.util.Map;

public interface IDataService extends IDataReadService {
    DataResponse insertRow(TableInsertRequest request, Long userId);
    DataResponse deleteRowById(String tableName, Long id, Long userId);
    DataResponse updateRowById(String tableName, Long id, Map<String, Object> updateData, Long userId);
}