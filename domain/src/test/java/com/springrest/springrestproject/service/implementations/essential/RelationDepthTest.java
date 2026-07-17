package com.springrest.springrestproject.service.implementations.essential;

import com.springrest.springrestproject.BaseIntegrationTest;
import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.dto.request.query.QueryRequest;
import com.springrest.springrestproject.service.interfaces.IDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RelationDepthTest extends BaseIntegrationTest {

    @Autowired
    private IDataService dataService;

    @Test
    void shouldAllowDepthUpToThree() {
        // Depth 0: no relations
        QueryRequest depth0 = new QueryRequest("app_users", null, null, null, null, Map.of());
        try {
            dataService.executeSelect(depth0, null, PageRequest.of(0, 10));
        } catch (ApplicationException ex) {
            // It might throw TABLE_NOT_FOUND or other errors, but it should not be due to depth cap.
            if (ex.getArgs() != null && ex.getArgs().length > 0 && "Relation depth exceeds the maximum cap of 3 levels".equals(ex.getArgs()[0])) {
                fail("Should not exceed cap for depth 0");
            }
        }

        // Depth 1: A -> B
        QueryRequest depth1 = new QueryRequest("app_users", null, null, null, null, Map.of(
            "ref1", new QueryRequest("table_metadata", null, null, null, null, Map.of())
        ));
        try {
            dataService.executeSelect(depth1, null, PageRequest.of(0, 10));
        } catch (ApplicationException ex) {
            if (ex.getArgs() != null && ex.getArgs().length > 0 && "Relation depth exceeds the maximum cap of 3 levels".equals(ex.getArgs()[0])) {
                fail("Should not exceed cap for depth 1");
            }
        }

        // Depth 3: A -> B -> C -> D
        QueryRequest d = new QueryRequest("d", null, null, null, null, Map.of());
        QueryRequest c = new QueryRequest("c", null, null, null, null, Map.of("relD", d));
        QueryRequest b = new QueryRequest("b", null, null, null, null, Map.of("relC", c));
        QueryRequest depth3 = new QueryRequest("app_users", null, null, null, null, Map.of("relB", b));

        try {
            dataService.executeSelect(depth3, null, PageRequest.of(0, 10));
        } catch (ApplicationException ex) {
            if (ex.getArgs() != null && ex.getArgs().length > 0 && "Relation depth exceeds the maximum cap of 3 levels".equals(ex.getArgs()[0])) {
                fail("Should not exceed cap for depth 3");
            }
        }
    }

    @Test
    void shouldRejectDepthFour() {
        // Depth 4: A -> B -> C -> D -> E
        QueryRequest e = new QueryRequest("e", null, null, null, null, Map.of());
        QueryRequest d = new QueryRequest("d", null, null, null, null, Map.of("relE", e));
        QueryRequest c = new QueryRequest("c", null, null, null, null, Map.of("relD", d));
        QueryRequest b = new QueryRequest("b", null, null, null, null, Map.of("relC", c));
        QueryRequest depth4 = new QueryRequest("app_users", null, null, null, null, Map.of("relB", b));

        ApplicationException ex = assertThrows(ApplicationException.class, () ->
            dataService.executeSelect(depth4, null, PageRequest.of(0, 10))
        );
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertNotNull(ex.getArgs());
        assertTrue(ex.getArgs().length > 0);
        assertEquals("Relation depth exceeds the maximum cap of 3 levels", ex.getArgs()[0]);
    }
}
