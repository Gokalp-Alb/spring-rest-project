package com.springrest.springrestproject.service.implementations.essential;

import com.springrest.springrestproject.BaseIntegrationTest;
import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.core.exception.FieldValidationError;
import com.springrest.springrestproject.dto.request.data.TableInsertRequest;
import com.springrest.springrestproject.dto.request.query.QueryRequest;
import com.springrest.springrestproject.dto.request.relation.DirectRelationRequest;
import com.springrest.springrestproject.dto.request.relation.ManyToManyInsertRequest;
import com.springrest.springrestproject.dto.request.relation.ManyToManyRelationRequest;
import com.springrest.springrestproject.dto.request.table.TableCreateRequest;
import com.springrest.springrestproject.dto.response.data.QueryResponse;
import com.springrest.springrestproject.model.column.ColumnContext;
import com.springrest.springrestproject.model.column.ColumnMetadata;
import com.springrest.springrestproject.model.column.ValidRegexPatterns;
import com.springrest.springrestproject.model.relation.DeletePolicy;
import com.springrest.springrestproject.model.table.TableMetadata;
import com.springrest.springrestproject.repository.TableMetadataRepo;
import com.springrest.springrestproject.service.interfaces.IDataService;
import com.springrest.springrestproject.service.interfaces.IMetadataService;
import com.springrest.springrestproject.service.interfaces.IRelationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class TableRelationshipIntegrationTest extends BaseIntegrationTest {

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
    private final String regexTable = "t_regex_test";

    @Autowired
    private com.springrest.springrestproject.service.implementations.redis.TableMetadataCacheService tableMetadataCacheService;

    @Autowired
    private com.springrest.springrestproject.service.implementations.redis.RelationCacheService relationCacheService;

    @Autowired
    private org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void setUp() {
        if (redisTemplate.getConnectionFactory() != null) {
            redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        }
        cleanup();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @SuppressWarnings({"SqlDialectInspection", "SqlNoDataSourceInspection", "SqlResolve"})
    private void cleanup() {
        try { jdbcTemplate.execute("DROP TABLE IF EXISTS " + childTable + " CASCADE;"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DROP TABLE IF EXISTS " + parentTable + " CASCADE;"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DROP TABLE IF EXISTS course_student_jt CASCADE;"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DROP TABLE IF EXISTS " + courseTable + " CASCADE;"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DROP TABLE IF EXISTS " + studentTable + " CASCADE;"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DROP TABLE IF EXISTS " + regexTable + " CASCADE;"); } catch (Exception ignored) {}

        // Scoped to this test's own dynamic tables only - sys_table_metadata/sys_relation_metadata now also
        // hold the seeded registry rows for every sys_ system table, so a blanket TRUNCATE would wipe those too.
        List<String> ownTables = List.of(parentTable, childTable, courseTable, studentTable, regexTable, "course_student_jt");
        try {
            jdbcTemplate.execute("DELETE FROM sys_relation_metadata WHERE source_table IN ('" + String.join("','", ownTables) + "') OR target_table IN ('" + String.join("','", ownTables) + "')");
        } catch (Exception ignored) {}
        try {
            jdbcTemplate.execute("DELETE FROM sys_table_metadata WHERE table_name IN ('" + String.join("','", ownTables) + "')");
        } catch (Exception ignored) {}

        tableMetadataCacheService.evict(childTable);
        tableMetadataCacheService.evict(parentTable);
        tableMetadataCacheService.evict(courseTable);
        tableMetadataCacheService.evict(studentTable);
        tableMetadataCacheService.evict(regexTable);
        tableMetadataCacheService.evict("course_student_jt");

        relationCacheService.evict(childTable);
        relationCacheService.evict(parentTable);
        relationCacheService.evict(courseTable);
        relationCacheService.evict(studentTable);
        relationCacheService.evict(regexTable);
        relationCacheService.evict("course_student_jt");
    }

    @Test
    void shouldCreateOneToOneRelationshipAndHandleForeignKeyErrors() {
        // 1. Create Parent Table
        List<ColumnMetadata> parentCols = new ArrayList<>();
        ColumnMetadata nameCol = ColumnMetadata.builder()
                .columnName("name")
                .dataType("VARCHAR(255)")
                .build();
        parentCols.add(nameCol);
        metadataService.createTable(parentTable, new TableCreateRequest(parentCols, false), 0L);

        // 2. Create Child Table (Empty schema initially)
        metadataService.createTable(childTable, new TableCreateRequest(new ArrayList<>(), false), 0L);
        
        // 3. Add ONE_TO_ONE relation dynamically
        relationService.createOneToOneRelation(new DirectRelationRequest(childTable, parentTable, DeletePolicy.CASCADE), 123L);

        TableMetadata childMeta = tableMetadataRepo.findByTableName(childTable).orElseThrow();
        ColumnMetadata parentIdCol = childMeta.columns().stream()
                .filter(c -> (parentTable + "_id").equals(c.columnName()))
                .findFirst()
                .orElseThrow();
        assertNotNull(parentIdCol.columnContext());
        assertEquals(123L, parentIdCol.columnContext().creatorId());
        assertEquals(123L, parentIdCol.columnContext().lastUpdaterId());

        // 4. Insert into Parent
        var insertParentRes = dataService.insertRow(new TableInsertRequest(parentTable, Map.of("name", "Parent 1")), 0L);
        Long parentId = insertParentRes.id();
        assertNotNull(parentId);

        // 5. Insert into Child pointing to valid parent id
        var insertChildRes = dataService.insertRow(new TableInsertRequest(childTable, Map.of(parentTable + "_id", parentId)), 0L);
        assertNotNull(insertChildRes.id());

        // 6. Try to insert invalid foreign key reference
        ApplicationException ex = assertThrows(ApplicationException.class, () ->
            dataService.insertRow(new TableInsertRequest(childTable, Map.of(parentTable + "_id", 99999)), 0L)
        );

        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertNotNull(ex.getErrors());
        assertEquals(1, ex.getErrors().size());
        FieldValidationError fve = ex.getErrors().getFirst();
        assertEquals(parentTable + "_id", fve.field());
        assertTrue(fve.reason().contains("99999"));
    }

    @Test
    void shouldApplyCascadeDeletePolicy() {
        metadataService.createTable(parentTable, new TableCreateRequest(new ArrayList<>(), false), 0L);
        metadataService.createTable(childTable, new TableCreateRequest(new ArrayList<>(), false), 0L);
        
        relationService.createOneToOneRelation(new DirectRelationRequest(childTable, parentTable, DeletePolicy.CASCADE), 0L);

        metadataService.deleteTableByName(parentTable, 0L);

        assertFalse(tableMetadataRepo.findByTableName(parentTable).isPresent());
    }

    @Test
    void shouldApplyRestrictDeletePolicy() {
        metadataService.createTable(parentTable, new TableCreateRequest(new ArrayList<>(), false), 0L);
        metadataService.createTable(childTable, new TableCreateRequest(new ArrayList<>(), false), 0L);
        
        relationService.createOneToOneRelation(new DirectRelationRequest(childTable, parentTable, DeletePolicy.RESTRICT), 0L);

        ApplicationException ex = assertThrows(ApplicationException.class, () ->
            metadataService.deleteTableByName(parentTable, 0L)
        );
        assertEquals(ErrorCode.RELATION_RESTRICT, ex.getErrorCode());
    }

    @Test
    void shouldApplySetNullDeletePolicy() {
        List<ColumnMetadata> parentCols = new ArrayList<>();
        ColumnMetadata nameCol = ColumnMetadata.builder()
                .columnName("name")
                .dataType("VARCHAR(255)")
                .build();
        parentCols.add(nameCol);
        
        metadataService.createTable(parentTable, new TableCreateRequest(parentCols, false), 0L);
        metadataService.createTable(childTable, new TableCreateRequest(new ArrayList<>(), false), 0L);
        
        relationService.createOneToOneRelation(new DirectRelationRequest(childTable, parentTable, DeletePolicy.SET_NULL), 0L);

        var parentRes = dataService.insertRow(new TableInsertRequest(parentTable, Map.of("name", "P")), 0L);
        dataService.insertRow(new TableInsertRequest(childTable, Map.of(parentTable + "_id", parentRes.id())), 0L);

        metadataService.deleteTableByName(parentTable, 0L);

        assertFalse(tableMetadataRepo.findByTableName(parentTable).isPresent());

        List<Map<String, Object>> childRows = jdbcTemplate.queryForList("SELECT * FROM " + childTable);
        assertEquals(1, childRows.size());
        assertFalse(childRows.getFirst().containsKey(parentTable + "_id"));
    }

    @Test
    void shouldCreateManyToManyRelationshipAndInsertData() {
        List<ColumnMetadata> cols = new ArrayList<>();
        ColumnMetadata nameCol = ColumnMetadata.builder()
                .columnName("name")
                .dataType("VARCHAR(255)")
                .build();
        cols.add(nameCol);
        
        metadataService.createTable(courseTable, new TableCreateRequest(cols, false), 0L);
        metadataService.createTable(studentTable, new TableCreateRequest(cols, false), 0L);

        relationService.createManyToManyRelation(new ManyToManyRelationRequest(courseTable, studentTable, DeletePolicy.CASCADE, DeletePolicy.CASCADE), 0L);

        assertTrue(tableMetadataRepo.findByTableName("course_student_jt").isPresent());

        var courseRes = dataService.insertRow(new TableInsertRequest(courseTable, Map.of("name", "Math")), 0L);
        var studentRes = dataService.insertRow(new TableInsertRequest(studentTable, Map.of("name", "Alice")), 0L);

        ManyToManyInsertRequest manyToManyInsertRequest = new ManyToManyInsertRequest(courseRes.id(), studentRes.id());
        relationService.insertManyToManyDataByName("course_student_jt", manyToManyInsertRequest);

        String junctionTable = "course_student_jt";
        List<Map<String, Object>> mappings = jdbcTemplate.queryForList("SELECT * FROM " + junctionTable);
        assertEquals(1, mappings.size());
        assertEquals(courseRes.id(), mappings.getFirst().get("course_id"));
        assertEquals(studentRes.id(), mappings.getFirst().get("student_id"));
        
        relationService.deleteManyToManyDataByName("course_student_jt", manyToManyInsertRequest);
        mappings = jdbcTemplate.queryForList("SELECT * FROM " + junctionTable);
        assertEquals(0, mappings.size());
    }

    @Test
    void shouldApplyCascadeDeletePolicyForManyToMany() {
        metadataService.createTable(courseTable, new TableCreateRequest(new ArrayList<>(), false), 0L);
        metadataService.createTable(studentTable, new TableCreateRequest(new ArrayList<>(), false), 0L);

        relationService.createManyToManyRelation(new ManyToManyRelationRequest(courseTable, studentTable, DeletePolicy.CASCADE, DeletePolicy.CASCADE), 0L);

        metadataService.deleteTableByName(courseTable, 0L);

        assertFalse(tableMetadataRepo.findByTableName("course_student_jt").isPresent());
    }

    @Test
    void shouldSuccessfullyJoinRelationsForO2O_O2M_M2M() {
        // --- 1. SETUP TABLES & DIRECT RELATION (O2O/O2M/M2O) ---
        List<ColumnMetadata> parentCols = new ArrayList<>();
        ColumnMetadata parentName = ColumnMetadata.builder()
                .columnName("name")
                .dataType("VARCHAR(255)")
                .build();
        parentCols.add(parentName);
        metadataService.createTable(parentTable, new TableCreateRequest(parentCols, false), 0L);
 
        List<ColumnMetadata> childCols = new ArrayList<>();
        ColumnMetadata childName = ColumnMetadata.builder()
                .columnName("val")
                .dataType("VARCHAR(255)")
                .build();
        childCols.add(childName);
        metadataService.createTable(childTable, new TableCreateRequest(childCols, false), 0L);
        
        relationService.createManyToOneRelation(new DirectRelationRequest(childTable, parentTable, DeletePolicy.CASCADE), 0L);
 
        // --- 2. SETUP M2M RELATION ---
        List<ColumnMetadata> courseCols = new ArrayList<>();
        ColumnMetadata courseName = ColumnMetadata.builder()
                .columnName("name")
                .dataType("VARCHAR(255)")
                .build();
        courseCols.add(courseName);
        metadataService.createTable(courseTable, new TableCreateRequest(courseCols, false), 0L);
 
        List<ColumnMetadata> studentCols = new ArrayList<>();
        ColumnMetadata studentName = ColumnMetadata.builder()
                .columnName("name")
                .dataType("VARCHAR(255)")
                .build();
        studentCols.add(studentName);
        metadataService.createTable(studentTable, new TableCreateRequest(studentCols, false), 0L);

        relationService.createManyToManyRelation(new ManyToManyRelationRequest(courseTable, studentTable, DeletePolicy.CASCADE, DeletePolicy.CASCADE), 0L);

        // --- 3. POPULATE DATA ---
        var parentRes1 = dataService.insertRow(new TableInsertRequest(parentTable, Map.of("name", "Parent A")), 0L);
        var parentRes2 = dataService.insertRow(new TableInsertRequest(parentTable, Map.of("name", "Parent B")), 0L);

        dataService.insertRow(new TableInsertRequest(childTable, Map.of("val", "Child 1", parentTable + "_id", parentRes1.id())), 0L);
        dataService.insertRow(new TableInsertRequest(childTable, Map.of("val", "Child 2", parentTable + "_id", parentRes1.id())), 0L);
        dataService.insertRow(new TableInsertRequest(childTable, Map.of("val", "Child 3", parentTable + "_id", parentRes2.id())), 0L);

        var courseRes1 = dataService.insertRow(new TableInsertRequest(courseTable, Map.of("name", "Math")), 0L);
        var courseRes2 = dataService.insertRow(new TableInsertRequest(courseTable, Map.of("name", "Science")), 0L);

        var studentRes1 = dataService.insertRow(new TableInsertRequest(studentTable, Map.of("name", "Alice")), 0L);
        var studentRes2 = dataService.insertRow(new TableInsertRequest(studentTable, Map.of("name", "Bob")), 0L);

        relationService.insertManyToManyDataByName("course_student_jt", new ManyToManyInsertRequest(courseRes1.id(), studentRes1.id()));
        relationService.insertManyToManyDataByName("course_student_jt", new ManyToManyInsertRequest(courseRes1.id(), studentRes2.id()));
        relationService.insertManyToManyDataByName("course_student_jt", new ManyToManyInsertRequest(courseRes2.id(), studentRes1.id()));

        // --- 4. EXECUTE & VERIFY JOINS ---
        Pageable pageable = PageRequest.of(0, 10);

        // 4.1. Reverse O2M Join: Query parents, join children (relation name "t_rel_child")
        QueryRequest queryParents = new QueryRequest(
                parentTable,
                List.of("id", "name"),
                List.of(),
                List.of(),
                List.of(),
                Map.of(childTable, new QueryRequest(childTable, null, null, null, null, null))
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

        // 4.2. Forward M2O Join: Query children, join parent (relation name "t_rel_parent")
        QueryRequest queryChildren = new QueryRequest(
                childTable,
                List.of("id", "val", parentTable + "_id"),
                List.of(),
                List.of(),
                List.of(),
                Map.of(
                    parentTable, new QueryRequest(parentTable, null, null, null, null, null)
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

        // 4.3. M2M Join: Query courses, join students (relation name "student")
        QueryRequest queryCourses = new QueryRequest(
                courseTable,
                List.of("id", "name"),
                List.of(),
                List.of(),
                List.of(),
                Map.of(studentTable, new QueryRequest(studentTable, null, null, null, null, null))
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

    @Test
    void shouldValidateRegexPatternsSuccessfullyAndFailOnMismatch() {
        // Test enum deserialization/creation
        assertEquals(ValidRegexPatterns.EMAIL, ValidRegexPatterns.fromValue("email"));
        assertEquals(ValidRegexPatterns.EMAIL, ValidRegexPatterns.fromValue("EMAIL"));
        assertEquals(ValidRegexPatterns.PHONE, ValidRegexPatterns.fromValue("phone"));
        assertEquals(ValidRegexPatterns.PHONE, ValidRegexPatterns.fromValue("PHONE"));

        ApplicationException creationEx = assertThrows(ApplicationException.class, () ->
            ValidRegexPatterns.fromValue("invalid_pattern")
        );
        assertEquals(ErrorCode.INVALID_REGEX_PATTERN_CREATION, creationEx.getErrorCode());
        assertTrue(creationEx.getArgs()[1].toString().contains("EMAIL"));
        assertTrue(creationEx.getArgs()[1].toString().contains("PHONE"));

        // 1. Create a table with an email column and a phone column
        List<ColumnMetadata> cols = new ArrayList<>();
        
        ColumnContext emailCtx = ColumnContext.builder()
                .validationRegex(ValidRegexPatterns.EMAIL)
                .build();
        ColumnMetadata emailCol = ColumnMetadata.builder()
                .columnName("email")
                .dataType("VARCHAR(255)")
                .columnContext(emailCtx)
                .build();
        cols.add(emailCol);
 
        ColumnContext phoneCtx = ColumnContext.builder()
                .validationRegex(ValidRegexPatterns.PHONE)
                .build();
        ColumnMetadata phoneCol = ColumnMetadata.builder()
                .columnName("phone")
                .dataType("VARCHAR(20)")
                .columnContext(phoneCtx)
                .build();
        cols.add(phoneCol);
 
        metadataService.createTable(regexTable, new TableCreateRequest(cols, false), 0L);

        // 2. Inserting a row with valid email and phone format should succeed
        assertDoesNotThrow(() ->
            dataService.insertRow(new TableInsertRequest(regexTable, Map.of(
                "email", "john.doe@example.com",
                "phone", "+905551234567"
            )), 0L)
        );

        // 3. Inserting a row with an invalid email format should throw ApplicationException with INVALID_REGEX_PATTERN
        ApplicationException exEmail = assertThrows(ApplicationException.class, () ->
            dataService.insertRow(new TableInsertRequest(regexTable, Map.of(
                "email", "invalid-email-address",
                "phone", "+123456"
            )), 0L)
        );
        assertEquals(ErrorCode.INVALID_REGEX_PATTERN, exEmail.getErrorCode());

        // 4. Inserting a row with an invalid phone format should throw ApplicationException with INVALID_REGEX_PATTERN
        ApplicationException exPhone = assertThrows(ApplicationException.class, () ->
            dataService.insertRow(new TableInsertRequest(regexTable, Map.of(
                "email", "valid@email.com",
                "phone", "123-abc-456"
            )), 0L)
        );
        assertEquals(ErrorCode.INVALID_REGEX_PATTERN, exPhone.getErrorCode());
    }
}
