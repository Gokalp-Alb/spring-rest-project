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
import com.springrest.springrestproject.dto.request.query.QueryRequest;
import com.springrest.springrestproject.dto.response.data.QueryResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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

    @Test
    void shouldSuccessfullyJoinRelationsForO2O_O2M_M2M() {
        // --- 1. SETUP TABLES & DIRECT RELATION (O2O/O2M/M2O) ---
        List<ColumnMetadata> parentCols = new ArrayList<>();
        ColumnMetadata parentName = new ColumnMetadata();
        parentName.setColumnName("name");
        parentName.setDataType("VARCHAR(255)");
        parentCols.add(parentName);
        metadataService.createTable(parentTable, new TableCreateRequest(parentCols, false), 0L);

        List<ColumnMetadata> childCols = new ArrayList<>();
        ColumnMetadata childName = new ColumnMetadata();
        childName.setColumnName("val");
        childName.setDataType("VARCHAR(255)");
        childCols.add(childName);
        metadataService.createTable(childTable, new TableCreateRequest(childCols, false), 0L);
        
        relationService.createManyToOneRelation(new DirectRelationRequest(childTable, "parent_id", parentTable, "id", DeletePolicy.CASCADE), 0L);

        // --- 2. SETUP M2M RELATION ---
        List<ColumnMetadata> courseCols = new ArrayList<>();
        ColumnMetadata courseName = new ColumnMetadata();
        courseName.setColumnName("name");
        courseName.setDataType("VARCHAR(255)");
        courseCols.add(courseName);
        metadataService.createTable(courseTable, new TableCreateRequest(courseCols, false), 0L);

        List<ColumnMetadata> studentCols = new ArrayList<>();
        ColumnMetadata studentName = new ColumnMetadata();
        studentName.setColumnName("name");
        studentName.setDataType("VARCHAR(255)");
        studentCols.add(studentName);
        metadataService.createTable(studentTable, new TableCreateRequest(studentCols, false), 0L);

        relationService.createManyToManyRelation(new ManyToManyRelationRequest(courseTable, studentTable, DeletePolicy.CASCADE, DeletePolicy.CASCADE), 0L);

        // --- 3. POPULATE DATA ---
        var parentRes1 = dataService.insertRow(new TableInsertRequest(parentTable, Map.of("name", "Parent A")), 0L);
        var parentRes2 = dataService.insertRow(new TableInsertRequest(parentTable, Map.of("name", "Parent B")), 0L);

        dataService.insertRow(new TableInsertRequest(childTable, Map.of("val", "Child 1", "parent_id", parentRes1.id())), 0L);
        dataService.insertRow(new TableInsertRequest(childTable, Map.of("val", "Child 2", "parent_id", parentRes1.id())), 0L);
        dataService.insertRow(new TableInsertRequest(childTable, Map.of("val", "Child 3", "parent_id", parentRes2.id())), 0L);

        var courseRes1 = dataService.insertRow(new TableInsertRequest(courseTable, Map.of("name", "Math")), 0L);
        var courseRes2 = dataService.insertRow(new TableInsertRequest(courseTable, Map.of("name", "Science")), 0L);

        var studentRes1 = dataService.insertRow(new TableInsertRequest(studentTable, Map.of("name", "Alice")), 0L);
        var studentRes2 = dataService.insertRow(new TableInsertRequest(studentTable, Map.of("name", "Bob")), 0L);

        relationService.insertManyToManyDataByName("course_student", new ManyToManyInsertRequest(courseRes1.id(), studentRes1.id()));
        relationService.insertManyToManyDataByName("course_student", new ManyToManyInsertRequest(courseRes1.id(), studentRes2.id()));
        relationService.insertManyToManyDataByName("course_student", new ManyToManyInsertRequest(courseRes2.id(), studentRes1.id()));

        // --- 4. EXECUTE & VERIFY JOINS ---
        Pageable pageable = PageRequest.of(0, 10);

        // 4.1. Reverse O2M Join: Query parents, join children (relation name "t_rel_child")
        QueryRequest queryParents = new QueryRequest(
                parentTable,
                List.of("id", "name"),
                List.of(),
                List.of(),
                List.of(),
                List.of(new QueryRequest.RelationQuery(childTable))
        );
        QueryResponse resParents = dataService.executeSelect(queryParents, 0L, pageable);
        assertEquals(2, resParents.data().size());
        
        // Find Parent A in results
        Map<String, Object> parentARow = resParents.data().stream()
                .filter(row -> "Parent A".equalsIgnoreCase(String.valueOf(row.get("name")).trim()))
                .findFirst().orElse(null);
        assertNotNull(parentARow);
        List<?> childrenOfA = (List<?>) parentARow.get(childTable);
        assertNotNull(childrenOfA);
        assertEquals(2, childrenOfA.size()); // Should have Child 1 and Child 2

        // 4.2. Forward M2O Join: Query children, join parent (relation name "t_rel_parent" or "t_rel_parent_via_parent_id")
        QueryRequest queryChildren = new QueryRequest(
                childTable,
                List.of("id", "val", "parent_id"),
                List.of(),
                List.of(),
                List.of(),
                List.of(
                    new QueryRequest.RelationQuery(parentTable),
                    new QueryRequest.RelationQuery(parentTable + "_via_parent_id")
                )
        );
        QueryResponse resChildren = dataService.executeSelect(queryChildren, 0L, pageable);
        assertEquals(3, resChildren.data().size());

        Map<String, Object> child1Row = resChildren.data().stream()
                .filter(row -> "Child 1".equalsIgnoreCase(String.valueOf(row.get("val")).trim()))
                .findFirst().orElse(null);
        assertNotNull(child1Row);

        // Verify target table name join (simple)
        List<?> parentSimple = (List<?>) child1Row.get(parentTable);
        assertNotNull(parentSimple);
        assertEquals(1, parentSimple.size());
        assertEquals("Parent A", ((Map<?, ?>) parentSimple.getFirst()).get("name"));

        // Verify explicit via join
        List<?> parentVia = (List<?>) child1Row.get(parentTable + "_via_parent_id");
        assertNotNull(parentVia);
        assertEquals(1, parentVia.size());
        assertEquals("Parent A", ((Map<?, ?>) parentVia.getFirst()).get("name"));

        // 4.3. M2M Join: Query courses, join students (relation name "student")
        QueryRequest queryCourses = new QueryRequest(
                courseTable,
                List.of("id", "name"),
                List.of(),
                List.of(),
                List.of(),
                List.of(new QueryRequest.RelationQuery(studentTable))
        );
        QueryResponse resCourses = dataService.executeSelect(queryCourses, 0L, pageable);
        assertEquals(2, resCourses.data().size());

        Map<String, Object> mathRow = resCourses.data().stream()
                .filter(row -> "Math".equalsIgnoreCase(String.valueOf(row.get("name")).trim()))
                .findFirst().orElse(null);
        assertNotNull(mathRow);
        List<?> mathStudents = (List<?>) mathRow.get(studentTable);
        assertNotNull(mathStudents);
        assertEquals(2, mathStudents.size()); // Alice and Bob
    }
}
