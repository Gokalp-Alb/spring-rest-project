package com.springrest.springrestproject.service.interfaces;

import com.springrest.springrestproject.dto.request.data.TableInsertRequest;

public interface IDataService{
    void insertRow(TableInsertRequest request, Long userId);
}