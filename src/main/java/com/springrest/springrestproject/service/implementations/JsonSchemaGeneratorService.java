package com.springrest.springrestproject.service.implementations;

import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.dto.response.relation.ResolvedRelation;
import com.springrest.springrestproject.model.column.ColumnMetadata;
import com.springrest.springrestproject.model.table.TableMetadata;
import com.springrest.springrestproject.repository.TableMetadataRepo;
import com.springrest.springrestproject.service.interfaces.IJsonSchemaGeneratorService;
import com.springrest.springrestproject.service.interfaces.IRelationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class JsonSchemaGeneratorService implements IJsonSchemaGeneratorService {
    private final TableMetadataRepo tableMetadataRepo;
    private final IRelationService relationService;

    @Override
    public Map<String, Object> generateSchemaForTable(String tableName) {
        Map<String, Map<String, Object>> defs = new LinkedHashMap<>();
        
        generateTableSchemaInternal(tableName, defs);
        
        Map<String, Object> rootSchema = new LinkedHashMap<>();
        rootSchema.put("$schema", "http://json-schema.org/draft-07/schema#");
        rootSchema.put("$ref", "#/$defs/" + tableName);
        rootSchema.put("$defs", defs);
        
        return rootSchema;
    }

    private void generateTableSchemaInternal(String tableName, Map<String, Map<String, Object>> defs) {
        if (defs.containsKey(tableName)) {
            return;
        }

        Map<String, Object> tableSchema = new LinkedHashMap<>();
        defs.put(tableName, tableSchema);

        TableMetadata metadata = tableMetadataRepo.findByTableName(tableName)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));

        tableSchema.put("type", "object");
        tableSchema.put("title", tableName);

        Map<String, Object> properties = new LinkedHashMap<>();
        tableSchema.put("properties", properties);

        // PK and System Columns
        properties.put("id", Map.of("type", "integer"));
        properties.put("creator_id", Map.of("type", "integer"));
        properties.put("created_date", Map.of("type", "string", "format", "date-time"));
        properties.put("last_updater_id", Map.of("type", "integer"));
        properties.put("last_changed_date", Map.of("type", "string", "format", "date-time"));

        // Table Columns
        if (metadata.getColumns() != null) {
            for (ColumnMetadata col : metadata.getColumns()) {
                properties.put(col.getColumnName(), parseDataTypeToJsonSchema(col.getDataType()));
            }
        }

        // Relations
        List<ResolvedRelation> relations = relationService.getRelationsForTable(tableName);
        if (relations != null) {
            for (ResolvedRelation rel : relations) {
                generateTableSchemaInternal(rel.targetTable(), defs);

                Map<String, Object> relationProperty = new LinkedHashMap<>();
                relationProperty.put("type", "array");
                relationProperty.put("items", Map.of("$ref", "#/$defs/" + rel.targetTable()));
                
                properties.put(rel.relationName(), relationProperty);
            }
        }
    }

    private Map<String, Object> parseDataTypeToJsonSchema(String dataType) {
        Map<String, Object> schema = new LinkedHashMap<>();
        if (dataType == null) {
            schema.put("type", "string");
            return schema;
        }

        String cleaned = dataType.trim().toUpperCase();

        // VARCHAR(n)
        Pattern varcharPattern = Pattern.compile("^(VARCHAR|CHARACTER VARYING|CHAR)\\s*\\(\\s*(\\d+)\\s*\\)$");
        Matcher varcharMatcher = varcharPattern.matcher(cleaned);
        if (varcharMatcher.matches()) {
            schema.put("type", "string");
            schema.put("maxLength", Integer.parseInt(varcharMatcher.group(2)));
            return schema;
        }

        // TEXT / CLOB
        if (cleaned.startsWith("TEXT") || cleaned.startsWith("CLOB")) {
            schema.put("type", "string");
            return schema;
        }

        // integers
        if (cleaned.startsWith("INT") || cleaned.startsWith("INTEGER") || cleaned.startsWith("SERIAL") 
                || cleaned.startsWith("BIGINT") || cleaned.startsWith("BIGSERIAL") || cleaned.startsWith("SMALLINT")) {
            schema.put("type", "integer");
            return schema;
        }

        // decimal / float
        if (cleaned.startsWith("NUMERIC") || cleaned.startsWith("DECIMAL") || cleaned.startsWith("REAL") 
                || cleaned.startsWith("DOUBLE") || cleaned.startsWith("FLOAT")) {
            schema.put("type", "number");
            return schema;
        }

        // boolean
        if (cleaned.startsWith("BOOLEAN") || cleaned.startsWith("BOOL")) {
            schema.put("type", "boolean");
            return schema;
        }

        // date/time
        if (cleaned.startsWith("TIMESTAMP") || cleaned.startsWith("DATE") || cleaned.startsWith("TIME")) {
            schema.put("type", "string");
            schema.put("format", "date-time");
            return schema;
        }

        // Default fallback
        schema.put("type", "string");
        return schema;
    }
}
