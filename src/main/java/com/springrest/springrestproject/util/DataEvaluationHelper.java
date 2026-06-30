package com.springrest.springrestproject.util;

import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.core.exception.FieldValidationError;
import com.springrest.springrestproject.dto.request.query.QueryRequest;
import com.springrest.springrestproject.dto.request.query.QueryRequest.Condition;
import com.springrest.springrestproject.model.column.ColumnMetadata;
import com.springrest.springrestproject.model.table.TableMetadata;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
                        String reason = String.format("regex invalid, expected regex = {%s}", regex);
                        throw new ApplicationException(
                                ErrorCode.INVALID_REGEX_PATTERN,
                                List.of(new FieldValidationError(columnName, reason)),
                                columnName,
                                reason
                        );
                    }
                }
            }
        }
    }

    public boolean isLogQuery(QueryRequest request) {
        if (request.conditions() == null) {
            return false;
        }
        for (Condition condition : request.conditions()) {
            if (condition.operator() != null && condition.operator().isLogQueryOperator()) {
                return true;
            }
        }
        return false;
    }

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Object parseIfDateTime(Object value) {
        if (value instanceof String str) {
            str = str.trim();
            if (str.matches("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$")) {
                try {
                    return LocalDateTime.parse(str, TIMESTAMP_FORMATTER);
                } catch (Exception ignored) {}
            }
            if (str.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
                try {
                    return LocalDate.parse(str);
                } catch (Exception ignored) {}
            }
        }
        return value;
    }

    public void validateRowDates(TableMetadata metadata, Map<String, Object> rowData) {
        for (ColumnMetadata col : metadata.getColumns()) {
            String columnName = col.getColumnName();
            if (rowData.containsKey(columnName) && rowData.get(columnName) != null) {
                String valueStr = String.valueOf(rowData.get(columnName));
                String dataType = col.getDataType().toUpperCase();

                if (dataType.contains("DATE") || dataType.contains("TIMESTAMP")) {
                    boolean valid = false;
                    String expectedFormatMessage = "yyyy-MM-dd or yyyy-MM-dd HH:mm:ss";

                    if (valueStr.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
                        try {
                            LocalDate.parse(valueStr);
                            valid = true;
                        } catch (Exception ignored) {}
                    } else if (valueStr.matches("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$")) {
                        try {
                            LocalDateTime.parse(valueStr, TIMESTAMP_FORMATTER);
                            valid = true;
                        } catch (Exception ignored) {}
                    }

                    if (!valid) {
                        String reason = String.format("invalid date/time, expected format = {%s}", expectedFormatMessage);
                        throw new ApplicationException(
                                ErrorCode.INVALID_DATE_FORMAT,
                                List.of(new FieldValidationError(columnName, reason)),
                                columnName,
                                valueStr,
                                expectedFormatMessage
                        );
                    }
                }
            }
        }
    }

    public void validateQueryDates(QueryRequest request) {
        if (request.conditions() == null) {
            return;
        }
        for (Condition condition : request.conditions()) {
            if ("executed_at".equalsIgnoreCase(condition.column())) {
                validateQueryValue(condition.column(), condition.value());
            }
        }
    }

    private void validateQueryValue(String column, Object value) {
        if (value instanceof List<?> list) {
            for (Object val : list) {
                validateSingleQueryValue(column, val);
            }
        } else if (value instanceof Object[] arr) {
            for (Object val : arr) {
                validateSingleQueryValue(column, val);
            }
        } else {
            String valStr = String.valueOf(value);
            if (valStr.contains(",")) {
                String[] parts = valStr.split(",");
                for (String part : parts) {
                    validateSingleQueryValue(column, part.trim());
                }
            } else {
                validateSingleQueryValue(column, value);
            }
        }
    }

    private void validateSingleQueryValue(String column, Object value) {
        String valueStr = String.valueOf(value);
        if (!valueStr.matches("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$")) {
            throwInvalidDateFormatException(column, valueStr);
        }
        try {
            LocalDateTime.parse(valueStr, TIMESTAMP_FORMATTER);
        } catch (Exception e) {
            throwInvalidDateFormatException(column, valueStr);
        }
    }

    private void throwInvalidDateFormatException(String column, String valueStr) {
        String reason = String.format("timestamp invalid, expected format = {%s}", "yyyy-MM-dd HH:mm:ss");
        throw new ApplicationException(
                ErrorCode.INVALID_DATE_FORMAT,
                List.of(new FieldValidationError(column, reason)),
                column,
                valueStr,
                "yyyy-MM-dd HH:mm:ss"
        );
    }
}
