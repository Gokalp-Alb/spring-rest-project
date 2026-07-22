package com.springrest.springrestproject.service.implementations.essential;

import com.springrest.springrestproject.BaseIntegrationTest;
import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.dto.request.data.TableInsertRequest;
import com.springrest.springrestproject.dto.request.table.TableCreateRequest;
import com.springrest.springrestproject.service.interfaces.IDataService;
import com.springrest.springrestproject.service.interfaces.IMetadataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class DataServiceGovernanceTest extends BaseIntegrationTest {

    @Autowired
    private IMetadataService metadataService;
    @Autowired
    private IDataService dataService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void cannotInsertRowWithIsRestrictedInPayload() {
        metadataService.createTable("widgets_governance", new TableCreateRequest(List.of(), false), 0L);
        ApplicationException ex = assertThrows(ApplicationException.class,
                () -> dataService.insertRow(new TableInsertRequest("widgets_governance", Map.of("is_restricted", true)), 0L));
        assertEquals(ErrorCode.RESTRICTED_ROW_MUTATION, ex.getErrorCode());
    }

    @Test
    void cannotUpdateRestrictedRow() {
        metadataService.createTable("widgets_governance2", new TableCreateRequest(List.of(), false), 0L);
        Long id = dataService.insertRow(new TableInsertRequest("widgets_governance2", Map.of()), 0L).id();
        jdbcTemplate.update("UPDATE widgets_governance2 SET is_restricted = true WHERE id = ?", id);

        ApplicationException ex = assertThrows(ApplicationException.class,
                () -> dataService.updateRowById("widgets_governance2", id, Map.of("val", "x"), 0L));
        assertEquals(ErrorCode.RESTRICTED_ROW_MUTATION, ex.getErrorCode());
    }

    @Test
    void cannotDeleteRestrictedRow() {
        metadataService.createTable("widgets_governance3", new TableCreateRequest(List.of(), false), 0L);
        Long id = dataService.insertRow(new TableInsertRequest("widgets_governance3", Map.of()), 0L).id();
        jdbcTemplate.update("UPDATE widgets_governance3 SET is_restricted = true WHERE id = ?", id);

        ApplicationException ex = assertThrows(ApplicationException.class,
                () -> dataService.deleteRowById("widgets_governance3", id, 0L));
        assertEquals(ErrorCode.RESTRICTED_ROW_MUTATION, ex.getErrorCode());
    }

    @Test
    void cannotChangeIsRestrictedViaUpdate() {
        metadataService.createTable("widgets_governance4", new TableCreateRequest(List.of(), false), 0L);
        Long id = dataService.insertRow(new TableInsertRequest("widgets_governance4", Map.of()), 0L).id();

        ApplicationException ex = assertThrows(ApplicationException.class,
                () -> dataService.updateRowById("widgets_governance4", id, Map.of("is_restricted", true), 0L));
        assertEquals(ErrorCode.RESTRICTED_ROW_MUTATION, ex.getErrorCode());
    }

    @Test
    void cannotMutateSysPrefixedTableThroughDataService() {
        ApplicationException ex = assertThrows(ApplicationException.class,
                () -> dataService.insertRow(new TableInsertRequest("sys_app_users", Map.of("username", "x")), 0L));
        assertEquals(ErrorCode.SYSTEM_TABLE_PROTECTED, ex.getErrorCode());
    }
}
