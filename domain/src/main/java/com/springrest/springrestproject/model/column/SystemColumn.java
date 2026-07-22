package com.springrest.springrestproject.model.column;

public record SystemColumn(
        ColumnDefinition creatorId,
        ColumnDefinition createdDate,
        ColumnDefinition lastUpdaterId,
        ColumnDefinition lastChangedDate,
        ColumnDefinition isRestricted
) {
    public record ColumnDefinition(String name, String type) {}

    public static SystemColumn defaults() {
        return new SystemColumn(
                new ColumnDefinition("creator_id", "BIGINT"),
                new ColumnDefinition("created_date", "TIMESTAMP"),
                new ColumnDefinition("last_updater_id", "BIGINT"),
                new ColumnDefinition("last_changed_date", "TIMESTAMP"),
                new ColumnDefinition("is_restricted", "BOOLEAN DEFAULT FALSE")
        );
    }
}
