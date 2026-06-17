package com.springrest.springrestproject.util;

import com.springrest.springrestproject.model.ColumnMetadata;
import com.springrest.springrestproject.model.TableMetadata;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DataEvaluationHelper {

    public String getProjectionClause(TableMetadata metadata, Boolean showSensitive) {
        boolean shouldRedact = (showSensitive != null) ? !showSensitive :
                (metadata.getAdminContext() != null && metadata.getAdminContext().isSensitive());
        if (!shouldRedact) {
            return "*";
        }
        List<String> columnProjections = new ArrayList<>();
        columnProjections.add("id");
        for (ColumnMetadata col : metadata.getColumns()) {
            if (!col.getColumnName().equalsIgnoreCase("id")) {
                columnProjections.add("'********' AS " + col.getColumnName());
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
}
