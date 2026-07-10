package com.springrest.springrestproject.service.interfaces.ReadServices;

import com.springrest.springrestproject.dto.response.relation.RelationResponse;
import com.springrest.springrestproject.dto.response.relation.ResolvedRelation;
import java.util.List;

public interface IRelationReadService {
    List<RelationResponse> getAllRelations();
    List<ResolvedRelation> getRelationsForTable(String tableName);
}
