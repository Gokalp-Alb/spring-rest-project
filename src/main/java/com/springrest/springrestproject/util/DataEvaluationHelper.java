package com.springrest.springrestproject.util;

import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.model.ColumnMetadata;
import com.springrest.springrestproject.model.TableMetadata;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DataEvaluationHelper {

    public String getProjectionClause(TableMetadata metadata, Boolean showSensitive) {
        List<String> columnProjections = new ArrayList<>();
        if (showSensitive == null) { showSensitive = false; }
        columnProjections.add("id");

        for (ColumnMetadata col : metadata.getColumns()) {
            if (col.getColumnName().equalsIgnoreCase("id")) {
                continue;
            }
            boolean shouldRedact = !showSensitive && col.getColumnContext().getIsSensitive();
            if (shouldRedact) {
                columnProjections.add("'********' AS " + col.getColumnName());
            } else {
                columnProjections.add(col.getColumnName());
            }
        }
        return String.join(", ", columnProjections);
    }

    public String rebuildFullSql(String parameterizedSql, List<Object> values) {
        if (values == null || values.isEmpty()) {
            return parameterizedSql;
        }

        String fullSql = parameterizedSql;
        for (Object val : values) {
            String stringValue;
            if (val == null) {
                stringValue = "NULL";
            } else if (val instanceof String) {
                stringValue = "'" + val.toString().replace("'", "''") + "'";
            } else {
                stringValue = val.toString();
            }
            fullSql = fullSql.replaceFirst("\\?", stringValue);
        }

        return fullSql;
    }

    public void validateRowRegex(TableMetadata metadata, Map<String, Object> rowData) {
        for (ColumnMetadata col : metadata.getColumns()) {
            String columnName = col.getColumnName();
            if (rowData.containsKey(columnName) && rowData.get(columnName) != null) {
                String valueStr = String.valueOf(rowData.get(columnName));
                if (col.getColumnContext() != null && col.getColumnContext().getValidationRegex() != null) {
                    String regex = col.getColumnContext().getValidationRegex();
                    Pattern pattern = Pattern.compile(regex);
                    Matcher matcher = pattern.matcher(valueStr);
                    if (!matcher.matches()) {
                        throw new ApplicationException(
                                ErrorCode.BAD_REQUEST,
                                String.format("Validation failed for column '%s'. Value does not match required pattern.", columnName)
                        );
                    }
                }
            }
        }
    }
}
