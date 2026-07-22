package com.springrest.springrestproject.service.implementations.essential;

import com.springrest.springrestproject.BaseIntegrationTest;
import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.dto.request.relation.DirectRelationRequest;
import com.springrest.springrestproject.dto.request.relation.ManyToManyRelationRequest;
import com.springrest.springrestproject.dto.request.table.TableCreateRequest;
import com.springrest.springrestproject.service.interfaces.IMetadataService;
import com.springrest.springrestproject.service.interfaces.IRelationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class RelationServiceGovernanceTest extends BaseIntegrationTest {

    @Autowired
    private IMetadataService metadataService;
    @Autowired
    private IRelationService relationService;

    @Test
    void cannotCreateManyToOneRelationTargetingSysTable() {
        metadataService.createTable("orders_governance", new TableCreateRequest(List.of(), false), 0L);
        ApplicationException ex = assertThrows(ApplicationException.class,
                () -> relationService.createManyToOneRelation(
                        new DirectRelationRequest("orders_governance", "sys_app_users", null), 0L));
        assertEquals(ErrorCode.SYSTEM_TABLE_PROTECTED, ex.getErrorCode());
    }

    @Test
    void cannotCreateManyToManyRelationInvolvingSysTable() {
        metadataService.createTable("tags_governance", new TableCreateRequest(List.of(), false), 0L);
        ApplicationException ex = assertThrows(ApplicationException.class,
                () -> relationService.createManyToManyRelation(
                        new ManyToManyRelationRequest("tags_governance", "sys_user_groups", null, null), 0L));
        assertEquals(ErrorCode.SYSTEM_TABLE_PROTECTED, ex.getErrorCode());
    }

    @Test
    void cannotCreateManyToOneRelationWithSourceSysTable() {
        metadataService.createTable("customers_governance", new TableCreateRequest(List.of(), false), 0L);
        ApplicationException ex = assertThrows(ApplicationException.class,
                () -> relationService.createManyToOneRelation(
                        new DirectRelationRequest("sys_app_users", "customers_governance", null), 0L));
        assertEquals(ErrorCode.SYSTEM_TABLE_PROTECTED, ex.getErrorCode());
    }
}
