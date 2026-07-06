package com.springrest.springrestproject.service.implementations.essential;

import com.springrest.springrestproject.BaseIntegrationTest;
import com.springrest.springrestproject.dto.request.relation.DirectRelationRequest;
import com.springrest.springrestproject.dto.request.table.TableCreateRequest;
import com.springrest.springrestproject.model.column.ColumnMetadata;
import com.springrest.springrestproject.model.relation.DeletePolicy;
import com.springrest.springrestproject.repository.TableMetadataRepo;
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
public class JsonSchemaGeneratorServiceTest extends BaseIntegrationTest {

    @Autowired
    private IMetadataService metadataService;

    @Autowired
    private IRelationService relationService;

    @Autowired
    private TableMetadataRepo tableMetadataRepo;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final String parentTable = "schema_parent";
    private final String childTable = "schema_child";

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

    private void cleanup() {
        try { jdbcTemplate.execute("DROP TABLE IF EXISTS " + childTable + " CASCADE;"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DROP TABLE IF EXISTS " + parentTable + " CASCADE;"); } catch (Exception ignored) {}

        tableMetadataRepo.findByTableName(childTable).ifPresent(metadata -> tableMetadataRepo.delete(metadata));
        tableMetadataRepo.findByTableName(parentTable).ifPresent(metadata -> tableMetadataRepo.delete(metadata));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldGenerateJsonSchemaWithCircularRelationsAndTypesTranslated() {
        // Create Parent Table with varied datatypes
        List<ColumnMetadata> parentCols = new ArrayList<>();

        ColumnMetadata nameCol = ColumnMetadata.builder()
                .columnName("name")
                .dataType("VARCHAR(120)")
                .build();
        parentCols.add(nameCol);

        ColumnMetadata ageCol = ColumnMetadata.builder()
                .columnName("age")
                .dataType("INTEGER")
                .build();
        parentCols.add(ageCol);

        ColumnMetadata isEmployeeCol = ColumnMetadata.builder()
                .columnName("is_employee")
                .dataType("BOOLEAN")
                .build();
        parentCols.add(isEmployeeCol);

        ColumnMetadata salaryCol = ColumnMetadata.builder()
                .columnName("salary")
                .dataType("DECIMAL(10,2)")
                .build();
        parentCols.add(salaryCol);

        metadataService.createTable(parentTable, new TableCreateRequest(parentCols, false), 0L);

        // Create Child Table
        List<ColumnMetadata> childCols = new ArrayList<>();
        
        ColumnMetadata titleCol = ColumnMetadata.builder()
                .columnName("title")
                .dataType("VARCHAR(80)")
                .build();
        childCols.add(titleCol);
        
        metadataService.createTable(childTable, new TableCreateRequest(childCols, false), 0L);

        // Establish bi-directional-like relations
        relationService.createManyToOneRelation(new DirectRelationRequest(childTable, "parent_id", parentTable, "id", DeletePolicy.CASCADE), 0L);

        // Generate JSON Schema
        Map<String, Object> schema = metadataService.generateSchemaForTable(parentTable);

        // Assertions
        assertNotNull(schema);
        assertEquals("http://json-schema.org/draft-07/schema#", schema.get("$schema"));
        assertEquals("#/$defs/" + parentTable, schema.get("$ref"));

        Map<String, Object> defs = (Map<String, Object>) schema.get("$defs");
        assertNotNull(defs);
        assertTrue(defs.containsKey(parentTable));
        assertTrue(defs.containsKey(childTable));

        // Assert parentTable schema
        Map<String, Object> parentSchema = (Map<String, Object>) defs.get(parentTable);
        assertEquals("object", parentSchema.get("type"));
        assertEquals(parentTable, parentSchema.get("title"));

        Map<String, Object> parentProps = (Map<String, Object>) parentSchema.get("properties");
        assertNotNull(parentProps);

        // System Columns
        assertTrue(parentProps.containsKey("id"));
        assertTrue(parentProps.containsKey("creator_id"));
        assertTrue(parentProps.containsKey("created_date"));
        
        // Custom VARCHAR translation
        Map<String, Object> nameProp = (Map<String, Object>) parentProps.get("name");
        assertEquals("string", nameProp.get("type"));
        assertEquals(120, nameProp.get("maxLength"));

        // Custom INTEGER translation
        Map<String, Object> ageProp = (Map<String, Object>) parentProps.get("age");
        assertEquals("integer", ageProp.get("type"));

        // Custom BOOLEAN translation
        Map<String, Object> isEmpProp = (Map<String, Object>) parentProps.get("is_employee");
        assertEquals("boolean", isEmpProp.get("type"));

        // Custom DECIMAL translation
        Map<String, Object> salaryProp = (Map<String, Object>) parentProps.get("salary");
        assertEquals("number", salaryProp.get("type"));

        // Assert childTable schema
        Map<String, Object> childSchema = (Map<String, Object>) defs.get(childTable);
        assertEquals("object", childSchema.get("type"));
        assertEquals(childTable, childSchema.get("title"));

        Map<String, Object> childProps = (Map<String, Object>) childSchema.get("properties");
        Map<String, Object> titleProp = (Map<String, Object>) childProps.get("title");
        assertEquals("string", titleProp.get("type"));
        assertEquals(80, titleProp.get("maxLength"));

        // Assert relational mappings point to defs
        // Child should have relation pointing back to parent
        Map<String, Object> childParentRel = (Map<String, Object>) childProps.get(parentTable);
        assertNotNull(childParentRel);
        assertEquals("array", childParentRel.get("type"));
        Map<String, Object> childParentItems = (Map<String, Object>) childParentRel.get("items");
        assertEquals("#/$defs/" + parentTable, childParentItems.get("$ref"));
    }
}
