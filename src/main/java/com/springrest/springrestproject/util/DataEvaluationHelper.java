package com.springrest.springrestproject.util;

import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.core.exception.FieldValidationError;
import com.springrest.springrestproject.dto.request.query.ALLOWED_OPERATION_TYPES;
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

        for (ColumnMetadata col : metadata.columns()) {
            if (col.columnName().equalsIgnoreCase("id")) {
                continue;
            }
            boolean shouldRedact = !showSensitive && col.columnContext() != null && Boolean.TRUE.equals(col.columnContext().isSensitive());
            if (shouldRedact) {
                columnProjections.add("'********' AS " + col.columnName());
            } else {
                columnProjections.add(col.columnName());
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
        for (ColumnMetadata col : metadata.columns()) {
            String columnName = col.columnName();
            if (rowData.containsKey(columnName) && rowData.get(columnName) != null) {
                String valueStr = String.valueOf(rowData.get(columnName));

                if (col.columnContext() != null && col.columnContext().validationRegex() != null) {
                    String regex = col.columnContext().validationRegex().getPattern();
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
        for (ColumnMetadata col : metadata.columns()) {
            String columnName = col.columnName();
            if (rowData.containsKey(columnName) && rowData.get(columnName) != null) {
                String valueStr = String.valueOf(rowData.get(columnName));
                String dataType = col.dataType().toUpperCase();

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
        if (request.conditions() != null) {
            for (Condition condition : request.conditions()) {
                if ("executed_at".equalsIgnoreCase(condition.column()) || 
                    "created_date".equalsIgnoreCase(condition.column()) || 
                    "last_changed_date".equalsIgnoreCase(condition.column())) {
                    validateQueryValue(condition.column(), condition.value());
                }
            }
        }
        if (request.audit() != null) {
            for (Condition condition : request.audit()) {
                if ("executed_at".equalsIgnoreCase(condition.column())) {
                    validateQueryValue(condition.column(), condition.value());
                }
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

    public List<String> parseOperationTypes(String value) {
        if (value == null) {
            return List.of();
        }
        String trimmed = value.trim();
        if ("*".equals(trimmed)) {
            return java.util.Arrays.stream(ALLOWED_OPERATION_TYPES.values())
                    .map(ALLOWED_OPERATION_TYPES::getValue)
                    .toList();
        }
        String[] parts = trimmed.split(",");
        List<String> mapped = new ArrayList<>();
        for (String part : parts) {
            String sanitized = part.trim().toUpperCase();
            if ("CREATE".equals(sanitized)) {
                mapped.add(ALLOWED_OPERATION_TYPES.POST.getValue());
            } else {
                try {
                    ALLOWED_OPERATION_TYPES op = ALLOWED_OPERATION_TYPES.fromValue(sanitized);
                    mapped.add(op.getValue());
                } catch (ApplicationException e) {
                    throw new ApplicationException(ErrorCode.INVALID_OPERATOR, part.trim(), "CREATE, PUT, DELETE");
                }
            }
        }
        return mapped;
    }
}
