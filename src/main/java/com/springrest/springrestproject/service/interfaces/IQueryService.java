package com.springrest.springrestproject.service.interfaces;

import com.springrest.springrestproject.dto.request.query.SelectQueryRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

public interface IQueryService {
    List<Map<String, Object>> executeSelect(SelectQueryRequest request, Long userId, Pageable pageable);
}