package com.springrest.springrestproject.service.interfaces;

import java.util.Map;

public interface IJsonSchemaGeneratorService {
    Map<String, Object> generateSchemaForTable(String tableName);
}
