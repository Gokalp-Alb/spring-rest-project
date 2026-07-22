package com.springrest.springrestproject.model.user;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class GroupNameTest {

    @Test
    void hasExactlySixValuesInDefinitionOrder() {
        assertArrayEquals(
                new GroupName[]{GroupName.ADMIN, GroupName.REGISTERED_USER, GroupName.SCRIPT_ENGINEER, GroupName.KAFKA_ENGINEER, GroupName.MCP_AGENT, GroupName.DATABASE_ADMIN},
                GroupName.values()
        );
    }
}
