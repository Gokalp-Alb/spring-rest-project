package com.springrest.springrestproject.service.implementations.essential;

import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.core.exception.FieldValidationError;
import com.springrest.springrestproject.dto.request.data.TableInsertRequest;
import com.springrest.springrestproject.dto.request.table.TableCreateRequest;
import com.springrest.springrestproject.model.column.ColumnMetadata;
import com.springrest.springrestproject.model.column.DeletePolicy;
import com.springrest.springrestproject.model.column.RelationType;
import com.springrest.springrestproject.repository.TableMetadataRepo;
import com.springrest.springrestproject.service.interfaces.IDataService;
import com.springrest.springrestproject.service.interfaces.IMetadataService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class TableRelationshipIntegrationTest {

    @Autowired
    private IDataService dataService;

    @Autowired
    private IMetadataService metadataService;

    @Autowired
    private TableMetadataRepo tableMetadataRepo;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final String parentTable = "t_rel_parent";
    private final String childTable = "t_rel_child";

    @BeforeEach
    void setUp() {
        cleanup();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        try {
            jdbcTemplate.execute("DROP TABLE IF EXISTS " + childTable + " CASCADE;");
        } catch (Exception ignored) {}
        try {
            jdbcTemplate.execute("DROP TABLE IF EXISTS " + parentTable + " CASCADE;");
        } catch (Exception ignored) {}

        tableMetadataRepo.findByTableName(childTable).ifPresent(metadata -> tableMetadataRepo.delete(metadata));
        tableMetadataRepo.findByTableName(parentTable).ifPresent(metadata -> tableMetadataRepo.delete(metadata));
    }

    @Test
    void shouldCreateOneToOneRelationshipAndHandleForeignKeyErrors() {
        // 1. Create Parent Table
        List<ColumnMetadata> parentCols = new ArrayList<>();
        ColumnMetadata nameCol = new ColumnMetadata();
        nameCol.setColumnName("name");
        nameCol.setDataType("VARCHAR(255)");
        parentCols.add(nameCol);
        metadataService.createTable(parentTable, new TableCreateRequest(parentCols, false), 0L);

        // 2. Create Child Table with ONE_TO_ONE relation to parent table
        List<ColumnMetadata> childCols = new ArrayList<>();
        ColumnMetadata fkCol = new ColumnMetadata();
        fkCol.setColumnName("parent_id");
        fkCol.setDataType("INT");
        fkCol.setRelationType(RelationType.ONE_TO_ONE);
        fkCol.setRelatedTable(parentTable);
        fkCol.setRelatedColumn("id");
        fkCol.setDeletePolicy(DeletePolicy.CASCADE);
        childCols.add(fkCol);
        metadataService.createTable(childTable, new TableCreateRequest(childCols, false), 0L);

        // 3. Insert into Parent
        var insertParentRes = dataService.insertRow(new TableInsertRequest(parentTable, Map.of("name", "Parent 1")), 0L);
        Long parentId = insertParentRes.id();
        assertNotNull(parentId);

        // 4. Insert into Child pointing to valid parent id
        var insertChildRes = dataService.insertRow(new TableInsertRequest(childTable, Map.of("parent_id", parentId)), 0L);
        assertNotNull(insertChildRes.id());

        // 5. Try to insert invalid foreign key reference (should throw parsed ApplicationException)
        ApplicationException ex = assertThrows(ApplicationException.class, () ->
            dataService.insertRow(new TableInsertRequest(childTable, Map.of("parent_id", 99999)), 0L)
        );

        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertNotNull(ex.getErrors());
        assertEquals(1, ex.getErrors().size());
        FieldValidationError fve = ex.getErrors().getFirst();
        assertEquals("parent_id", fve.field());
        assertTrue(fve.reason().contains("99999"));
    }

    @Test
    void shouldApplyCascadeDeletePolicy() {
        // Create Parent
        List<ColumnMetadata> parentCols = new ArrayList<>();
        ColumnMetadata nameCol = new ColumnMetadata();
        nameCol.setColumnName("name");
        nameCol.setDataType("VARCHAR(255)");
        parentCols.add(nameCol);
        metadataService.createTable(parentTable, new TableCreateRequest(parentCols, false), 0L);

        // Create Child with CASCADE delete policy
        List<ColumnMetadata> childCols = new ArrayList<>();
        ColumnMetadata fkCol = new ColumnMetadata();
        fkCol.setColumnName("parent_id");
        fkCol.setDataType("INT");
        fkCol.setRelationType(RelationType.ONE_TO_ONE);
        fkCol.setRelatedTable(parentTable);
        fkCol.setRelatedColumn("id");
        fkCol.setDeletePolicy(DeletePolicy.CASCADE);
        childCols.add(fkCol);
        metadataService.createTable(childTable, new TableCreateRequest(childCols, false), 0L);

        // Delete parent table
        metadataService.deleteTableByName(parentTable, 0L);

        // Verify parent is deleted, and child table drop constraint CASCADE drop was executed
        assertFalse(tableMetadataRepo.findByTableName(parentTable).isPresent());
    }

    @Test
    void shouldApplyRestrictDeletePolicy() {
        // Create Parent
        List<ColumnMetadata> parentCols = new ArrayList<>();
        ColumnMetadata nameCol = new ColumnMetadata();
        nameCol.setColumnName("name");
        nameCol.setDataType("VARCHAR(255)");
        parentCols.add(nameCol);
        metadataService.createTable(parentTable, new TableCreateRequest(parentCols, false), 0L);

        // Create Child with RESTRICT delete policy
        List<ColumnMetadata> childCols = new ArrayList<>();
        ColumnMetadata fkCol = new ColumnMetadata();
        fkCol.setColumnName("parent_id");
        fkCol.setDataType("INT");
        fkCol.setRelationType(RelationType.ONE_TO_ONE);
        fkCol.setRelatedTable(parentTable);
        fkCol.setRelatedColumn("id");
        fkCol.setDeletePolicy(DeletePolicy.RESTRICT);
        childCols.add(fkCol);
        metadataService.createTable(childTable, new TableCreateRequest(childCols, false), 0L);

        // Delete parent table (should throw RELATION_RESTRICT exception)
        ApplicationException ex = assertThrows(ApplicationException.class, () ->
            metadataService.deleteTableByName(parentTable, 0L)
        );
        assertEquals(ErrorCode.RELATION_RESTRICT, ex.getErrorCode());
    }

    @Test
    void shouldApplySetNullDeletePolicy() {
        // Create Parent
        List<ColumnMetadata> parentCols = new ArrayList<>();
        ColumnMetadata nameCol = new ColumnMetadata();
        nameCol.setColumnName("name");
        nameCol.setDataType("VARCHAR(255)");
        parentCols.add(nameCol);
        metadataService.createTable(parentTable, new TableCreateRequest(parentCols, false), 0L);

        // Create Child with SET_NULL delete policy
        List<ColumnMetadata> childCols = new ArrayList<>();
        ColumnMetadata fkCol = new ColumnMetadata();
        fkCol.setColumnName("parent_id");
        fkCol.setDataType("INT");
        fkCol.setRelationType(RelationType.ONE_TO_ONE);
        fkCol.setRelatedTable(parentTable);
        fkCol.setRelatedColumn("id");
        fkCol.setDeletePolicy(DeletePolicy.SET_NULL);
        childCols.add(fkCol);
        metadataService.createTable(childTable, new TableCreateRequest(childCols, false), 0L);

        // Insert into parent & child
        var parentRes = dataService.insertRow(new TableInsertRequest(parentTable, Map.of("name", "P")), 0L);
        dataService.insertRow(new TableInsertRequest(childTable, Map.of("parent_id", parentRes.id())), 0L);

        // Delete parent table
        metadataService.deleteTableByName(parentTable, 0L);

        // Verify parent is deleted from metadata
        assertFalse(tableMetadataRepo.findByTableName(parentTable).isPresent());

        // Verify child table row is nullified
        List<Map<String, Object>> childRows = jdbcTemplate.queryForList("SELECT parent_id FROM " + childTable);
        assertEquals(1, childRows.size());
        assertNull(childRows.getFirst().get("parent_id"));
    }
}
