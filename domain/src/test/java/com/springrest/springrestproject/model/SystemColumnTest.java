package com.springrest.springrestproject.model;

import com.springrest.springrestproject.model.column.SystemColumn;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SystemColumnTest {

    @Test
    void defaultsIncludesIsRestrictedColumn() {
        SystemColumn sysCols = SystemColumn.defaults();

        assertEquals("is_restricted", sysCols.isRestricted().name());
        assertEquals("BOOLEAN DEFAULT FALSE", sysCols.isRestricted().type());
    }
}
