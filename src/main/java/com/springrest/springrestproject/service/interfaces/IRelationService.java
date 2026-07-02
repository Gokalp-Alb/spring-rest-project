package com.springrest.springrestproject.service.interfaces;

import com.springrest.springrestproject.dto.request.relation.DirectRelationRequest;
import com.springrest.springrestproject.dto.request.relation.ManyToManyRelationRequest;
import com.springrest.springrestproject.dto.request.relation.ManyToManyInsertRequest;
import com.springrest.springrestproject.dto.response.relation.RelationResponse;
import com.springrest.springrestproject.dto.response.relation.ResolvedRelation;
import java.util.List;

public interface IRelationService {
    RelationResponse createOneToOneRelation(DirectRelationRequest request, Long userId);
    RelationResponse createManyToOneRelation(DirectRelationRequest request, Long userId);
    RelationResponse createManyToManyRelation(ManyToManyRelationRequest request, Long userId);
    
    ManyToManyInsertRequest insertManyToManyDataById(Long relationId, ManyToManyInsertRequest request);
    ManyToManyInsertRequest insertManyToManyDataByName(String tableName, ManyToManyInsertRequest request);

    void deleteManyToManyDataById(Long relationId, ManyToManyInsertRequest request);
    void deleteManyToManyDataByName(String tableName, ManyToManyInsertRequest request);

    List<RelationResponse> getAllRelations();
    List<ResolvedRelation> getRelationsForTable(String tableName);
}
