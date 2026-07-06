package com.springrest.springrestproject.service.implementations.redis;

import com.springrest.springrestproject.dto.request.relation.DirectRelationRequest;
import com.springrest.springrestproject.dto.request.relation.ManyToManyRelationRequest;
import com.springrest.springrestproject.dto.request.table.TableCreateRequest;
import com.springrest.springrestproject.model.column.ColumnMetadata;
import com.springrest.springrestproject.model.relation.DeletePolicy;
import com.springrest.springrestproject.model.table.TableMetadata;
import com.springrest.springrestproject.repository.TableMetadataRepo;
import com.springrest.springrestproject.repository.RelationMetadataRepo;
import com.springrest.springrestproject.service.interfaces.IMetadataService;
import com.springrest.springrestproject.service.interfaces.IRelationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.springrest.springrestproject.BaseIntegrationTest;

@SpringBootTest
public class SchemaCacheIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private IMetadataService metadataService;

    @Autowired
    private IRelationService relationService;

    @Autowired
    private TableMetadataRepo tableMetadataRepo;

    @Autowired
    private RelationMetadataRepo relationMetadataRepo;

    @Autowired
    private TableMetadataCacheService tableMetadataCacheService;

    @Autowired
    private RelationCacheService relationCacheService;

    private final String tableName1 = "cache_test_table_1";
    private final String tableName2 = "cache_test_table_2";
    private final String tableName3 = "cache_test_table_3";

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
        try { metadataService.deleteTableByName(tableName1, 1L); } catch (Exception ignored) {}
        try { metadataService.deleteTableByName(tableName2, 1L); } catch (Exception ignored) {}
        try { metadataService.deleteTableByName(tableName3, 1L); } catch (Exception ignored) {}
        try { metadataService.deleteTableByName(tableName1 + "_" + tableName2, 1L); } catch (Exception ignored) {}

        tableMetadataCacheService.evict(tableName1);
        tableMetadataCacheService.evict(tableName2);
        tableMetadataCacheService.evict(tableName3);
        relationCacheService.evict(tableName1);
        relationCacheService.evict(tableName2);
        relationCacheService.evict(tableName3);
    }

    private void createTable(String name) {
        List<ColumnMetadata> columns = new ArrayList<>();
        ColumnMetadata col1 = ColumnMetadata.builder()
                .columnName("dummy_col")
                .dataType("varchar(255)")
                .build();
        columns.add(col1);
        
        TableCreateRequest request = new TableCreateRequest(columns, false);
        metadataService.createTable(name, request, 1L);
    }

    @Test
    void testCachePopulationAndEvictionOnDelete() {
        createTable(tableName1);
        
        tableMetadataRepo.findByTableName(tableName1);
        
        Optional<TableMetadata> cached = tableMetadataCacheService.get(tableName1);
        assertTrue(cached.isPresent(), "Table should be cached after findByTableName is called.");
        assertEquals(tableName1, cached.get().tableName());

        metadataService.deleteTableByName(tableName1, 1L);
        
        Optional<TableMetadata> cachedAfterDelete = tableMetadataCacheService.get(tableName1);
        assertTrue(cachedAfterDelete.isEmpty(), "Table should be evicted after it is deleted.");
    }

    @Test
    void testCacheEvictionOnRelationAdd() {
        createTable(tableName1);
        createTable(tableName2);
        createTable(tableName3);

        tableMetadataRepo.findByTableName(tableName1);
        tableMetadataRepo.findByTableName(tableName3);
        assertTrue(tableMetadataCacheService.get(tableName1).isPresent());
        assertTrue(tableMetadataCacheService.get(tableName3).isPresent());

        DirectRelationRequest relReq = new DirectRelationRequest(
                tableName1, "fk_table2", tableName2, "id", DeletePolicy.CASCADE
        );
        relationService.createManyToOneRelation(relReq, 1L);

        Optional<TableMetadata> cachedTable1 = tableMetadataCacheService.get(tableName1);
        assertTrue(cachedTable1.isEmpty(), "Table1 should be evicted after a new relation is added to it.");

        Optional<TableMetadata> cachedTable3 = tableMetadataCacheService.get(tableName3);
        assertTrue(cachedTable3.isPresent(), "Table3 should NOT be evicted because it was entirely unrelated to the operation.");
    }
    
    @Test
    void testRelationCacheEvictionOnTableDelete() {
        createTable(tableName1);
        createTable(tableName2);
        
        DirectRelationRequest relReq = new DirectRelationRequest(
                tableName1, "fk_table2", tableName2, "id", DeletePolicy.CASCADE
        );
        relationService.createManyToOneRelation(relReq, 1L);
        
        relationMetadataRepo.findIncomingFKs(tableName2);
        assertTrue(relationCacheService.get(tableName2).isPresent());
        
        metadataService.deleteTableByName(tableName1, 1L);
        
        assertTrue(relationCacheService.get(tableName2).isEmpty(), "Table2's incoming FK cache should be evicted when the referencing table (table1) is deleted.");
    }

    @Test
    void testRelationCacheEvictionOnManyToManyRelationAdd() {
        createTable(tableName1);
        createTable(tableName2);

        // Populate relation cache
        relationMetadataRepo.findIncomingFKs(tableName1);
        relationMetadataRepo.findIncomingFKs(tableName2);
        assertTrue(relationCacheService.get(tableName1).isPresent());
        assertTrue(relationCacheService.get(tableName2).isPresent());

        // Create M2M relation
        ManyToManyRelationRequest m2mReq = new ManyToManyRelationRequest(
                tableName1, tableName2, DeletePolicy.CASCADE, DeletePolicy.CASCADE
        );
        relationService.createManyToManyRelation(m2mReq, 1L);

        // Verify both tables' incoming FK cache is evicted because the new junction table points to both
        assertTrue(relationCacheService.get(tableName1).isEmpty(), "Table1's incoming FK cache should be evicted on M2M creation.");
        assertTrue(relationCacheService.get(tableName2).isEmpty(), "Table2's incoming FK cache should be evicted on M2M creation.");
    }

    @Test
    void testRelationCacheEvictionOnManyToManyJunctionDelete() {
        createTable(tableName1);
        createTable(tableName2);

        ManyToManyRelationRequest m2mReq = new ManyToManyRelationRequest(
                tableName1, tableName2, DeletePolicy.CASCADE, DeletePolicy.CASCADE
        );
        relationService.createManyToManyRelation(m2mReq, 1L);

        // Populate relation cache
        relationMetadataRepo.findIncomingFKs(tableName1);
        relationMetadataRepo.findIncomingFKs(tableName2);
        assertTrue(relationCacheService.get(tableName1).isPresent());
        assertTrue(relationCacheService.get(tableName2).isPresent());

        // Delete table1
        metadataService.deleteTableByName(tableName1, 1L);

        // The junction table gets deleted automatically.
        // Therefore, table2 loses an incoming FK from the junction table.
        // Verify table2's relation cache is evicted.
        assertTrue(relationCacheService.get(tableName2).isEmpty(), "Table2's incoming FK cache should be evicted when the other parent of a junction table is deleted.");
    }
}
