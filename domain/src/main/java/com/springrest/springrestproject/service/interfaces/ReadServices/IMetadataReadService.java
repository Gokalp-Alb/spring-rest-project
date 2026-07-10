package com.springrest.springrestproject.service.interfaces.ReadServices;

import com.springrest.springrestproject.dto.response.table.TableResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Map;

public interface IMetadataReadService {
    Page<TableResponse> getAllTables(Pageable pageable);
    TableResponse getTableById(Long tableId);
    TableResponse getTableByName(String tableId);
    Map<String, Object> generateSchemaForTable(String tableName);
}
