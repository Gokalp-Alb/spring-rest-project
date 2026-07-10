package com.springrest.springrestproject.dto.response.relation;

import com.springrest.springrestproject.model.relation.RelationJoinType;

public record ResolvedRelation(
    String relationName,
    String targetTable,
    RelationJoinType type,   // M2M, FORWARD (M2O/O2O), REVERSE (O2M/O2O)
    String baseColumn,       // Column on base table (for FORWARD)
    String targetColumn,     // Column on target table (for REVERSE/FORWARD)
    String junctionTable,    // Junction table name (for M2M)
    String junctionBaseCol,  // Column pointing to base table in junction (for M2M)
    String junctionTargetCol // Column pointing to target table in junction (for M2M)
) {}
