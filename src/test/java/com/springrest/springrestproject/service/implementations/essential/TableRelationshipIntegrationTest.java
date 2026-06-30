package com.springrest.springrestproject.service.implementations.essential;

import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.core.exception.FieldValidationError;
import com.springrest.springrestproject.dto.request.data.TableInsertRequest;
import com.springrest.springrestproject.dto.request.relation.DirectRelationRequest;
import com.springrest.springrestproject.dto.request.relation.ManyToManyInsertRequest;
import com.springrest.springrestproject.dto.request.relation.ManyToManyRelationRequest;
import com.springrest.springrestproject.dto.request.table.TableCreateRequest;
import com.springrest.springrestproject.model.column.ColumnMetadata;
import com.springrest.springrestproject.model.relation.DeletePolicy;
import com.springrest.springrestproject.repository.TableMetadataRepo;
import com.springrest.springrestproject.service.interfaces.IDataService;
import com.springrest.springrestproject.service.interfaces.IMetadataService;
import com.springrest.springrestproject.service.interfaces.IRelationService;
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
    private IRelationService relationService;

    @Autowired
    private TableMetadataRepo tableMetadataRepo;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final String parentTable = "t_rel_parent";
    private final String childTable = "t_rel_child";
    private final String courseTable = "course";
    private final String studentTable = "student";

    @BeforeEach
    void setUp() {
        cleanup();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        try { jdbcTemplate.execute("DROP TABLE IF EXISTS " + childTable + " CASCADE;"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DROP TABLE IF EXISTS " + parentTable + " CASCADE;"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DROP TABLE IF EXISTS course_student CASCADE;"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DROP TABLE IF EXISTS " + courseTable + " CASCADE;"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DROP TABLE IF EXISTS " + studentTable + " CASCADE;"); } catch (Exception ignored) {}

        tableMetadataRepo.findByTableName(childTable).ifPresent(metadata -> tableMetadataRepo.delete(metadata));
        tableMetadataRepo.findByTableName(parentTable).ifPresent(metadata -> tableMetadataRepo.delete(metadata));
        tableMetadataRepo.findByTableName("course_student").ifPresent(metadata -> tableMetadataRepo.delete(metadata));
        tableMetadataRepo.findByTableName(courseTable).ifPresent(metadata -> tableMetadataRepo.delete(metadata));
        tableMetadataRepo.findByTableName(studentTable).ifPresent(metadata -> tableMetadataRepo.delete(metadata));
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

        // 2. Create Child Table (Empty schema initially)
        metadataService.createTable(childTable, new TableCreateRequest(new ArrayList<>(), false), 0L);
        
        // 3. Add ONE_TO_ONE relation dynamically
        relationService.createOneToOneRelation(new DirectRelationRequest(childTable, "parent_id", parentTable, "id", DeletePolicy.CASCADE), 0L);

        // 4. Insert into Parent
        var insertParentRes = dataService.insertRow(new TableInsertRequest(parentTable, Map.of("name", "Parent 1")), 0L);
        Long parentId = insertParentRes.id();
        assertNotNull(parentId);

        // 5. Insert into Child pointing to valid parent id
        var insertChildRes = dataService.insertRow(new TableInsertRequest(childTable, Map.of("parent_id", parentId)), 0L);
        assertNotNull(insertChildRes.id());

        // 6. Try to insert invalid foreign key reference
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
        metadataService.createTable(parentTable, new TableCreateRequest(new ArrayList<>(), false), 0L);
        metadataService.createTable(childTable, new TableCreateRequest(new ArrayList<>(), false), 0L);
        
        relationService.createOneToOneRelation(new DirectRelationRequest(childTable, "parent_id", parentTable, "id", DeletePolicy.CASCADE), 0L);

        metadataService.deleteTableByName(parentTable, 0L);

        assertFalse(tableMetadataRepo.findByTableName(parentTable).isPresent());
    }

    @Test
    void shouldApplyRestrictDeletePolicy() {
        metadataService.createTable(parentTable, new TableCreateRequest(new ArrayList<>(), false), 0L);
        metadataService.createTable(childTable, new TableCreateRequest(new ArrayList<>(), false), 0L);
        
        relationService.createOneToOneRelation(new DirectRelationRequest(childTable, "parent_id", parentTable, "id", DeletePolicy.RESTRICT), 0L);

        ApplicationException ex = assertThrows(ApplicationException.class, () ->
            metadataService.deleteTableByName(parentTable, 0L)
        );
        assertEquals(ErrorCode.RELATION_RESTRICT, ex.getErrorCode());
    }

    @Test
    void shouldApplySetNullDeletePolicy() {
        List<ColumnMetadata> parentCols = new ArrayList<>();
        ColumnMetadata nameCol = new ColumnMetadata();
        nameCol.setColumnName("name");
        nameCol.setDataType("VARCHAR(255)");
        parentCols.add(nameCol);
        
        metadataService.createTable(parentTable, new TableCreateRequest(parentCols, false), 0L);
        metadataService.createTable(childTable, new TableCreateRequest(new ArrayList<>(), false), 0L);
        
        relationService.createOneToOneRelation(new DirectRelationRequest(childTable, "parent_id", parentTable, "id", DeletePolicy.SET_NULL), 0L);

        var parentRes = dataService.insertRow(new TableInsertRequest(parentTable, Map.of("name", "P")), 0L);
        dataService.insertRow(new TableInsertRequest(childTable, Map.of("parent_id", parentRes.id())), 0L);

        metadataService.deleteTableByName(parentTable, 0L);

        assertFalse(tableMetadataRepo.findByTableName(parentTable).isPresent());

        List<Map<String, Object>> childRows = jdbcTemplate.queryForList("SELECT * FROM " + childTable);
        assertEquals(1, childRows.size());
        assertFalse(childRows.getFirst().containsKey("parent_id"));
    }

    @Test
    void shouldCreateManyToManyRelationshipAndInsertData() {
        List<ColumnMetadata> cols = new ArrayList<>();
        ColumnMetadata nameCol = new ColumnMetadata();
        nameCol.setColumnName("name");
        nameCol.setDataType("VARCHAR(255)");
        cols.add(nameCol);
        
        metadataService.createTable(courseTable, new TableCreateRequest(cols, false), 0L);
        metadataService.createTable(studentTable, new TableCreateRequest(cols, false), 0L);

        relationService.createManyToManyRelation(new ManyToManyRelationRequest(courseTable, studentTable, DeletePolicy.CASCADE, DeletePolicy.CASCADE), 0L);

        assertTrue(tableMetadataRepo.findByTableName("course_student").isPresent());

        var courseRes = dataService.insertRow(new TableInsertRequest(courseTable, Map.of("name", "Math")), 0L);
        var studentRes = dataService.insertRow(new TableInsertRequest(studentTable, Map.of("name", "Alice")), 0L);

        ManyToManyInsertRequest manyToManyInsertRequest = new ManyToManyInsertRequest(courseRes.id(), studentRes.id());
        relationService.insertManyToManyDataByName("course_student", manyToManyInsertRequest);

        String junctionTable = "course_student";
        List<Map<String, Object>> mappings = jdbcTemplate.queryForList("SELECT * FROM " + junctionTable);
        assertEquals(1, mappings.size());
        assertEquals(courseRes.id(), mappings.getFirst().get("course_id"));
        assertEquals(studentRes.id(), mappings.getFirst().get("student_id"));
        
        relationService.deleteManyToManyDataByName("course_student", manyToManyInsertRequest);
        mappings = jdbcTemplate.queryForList("SELECT * FROM " + junctionTable);
        assertEquals(0, mappings.size());
    }

    @Test
    void shouldApplyCascadeDeletePolicyForManyToMany() {
        metadataService.createTable(courseTable, new TableCreateRequest(new ArrayList<>(), false), 0L);
        metadataService.createTable(studentTable, new TableCreateRequest(new ArrayList<>(), false), 0L);

        relationService.createManyToManyRelation(new ManyToManyRelationRequest(courseTable, studentTable, DeletePolicy.CASCADE, DeletePolicy.CASCADE), 0L);

        metadataService.deleteTableByName(courseTable, 0L);

        assertFalse(tableMetadataRepo.findByTableName("course_student").isPresent());
    }
}
