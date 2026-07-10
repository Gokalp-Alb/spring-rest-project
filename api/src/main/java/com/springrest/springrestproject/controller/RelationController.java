package com.springrest.springrestproject.controller;

import com.springrest.springrestproject.core.response.ApiResponse;
import com.springrest.springrestproject.dto.request.relation.DirectRelationRequest;
import com.springrest.springrestproject.dto.request.relation.ManyToManyRelationRequest;
import com.springrest.springrestproject.dto.request.relation.ManyToManyInsertRequest;
import com.springrest.springrestproject.dto.response.relation.RelationResponse;
import jakarta.validation.Valid;
import com.springrest.springrestproject.service.interfaces.IRelationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/relations")
@RequiredArgsConstructor
public class RelationController {

    private final IRelationService relationService;

    @PostMapping("/one-to-one")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RelationResponse> createOneToOneRelation(@Valid @RequestBody DirectRelationRequest request,
                                                    @AuthenticationPrincipal Jwt jwt) {
        RelationResponse response = relationService.createOneToOneRelation(request, jwt.getClaim("userId"));
        return ApiResponse.success(HttpStatus.CREATED.value(), response);
    }

    @PostMapping("/many-to-one")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RelationResponse> createManyToOneRelation(@Valid @RequestBody DirectRelationRequest request,
                                                     @AuthenticationPrincipal Jwt jwt) {
        RelationResponse response = relationService.createManyToOneRelation(request, jwt.getClaim("userId"));
        return ApiResponse.success(HttpStatus.CREATED.value(), response);
    }

    @PostMapping("/many-to-many")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RelationResponse> createManyToManyRelation(@Valid @RequestBody ManyToManyRelationRequest request,
                                                      @AuthenticationPrincipal Jwt jwt) {
        RelationResponse response = relationService.createManyToManyRelation(request, jwt.getClaim("userId"));
        return ApiResponse.success(HttpStatus.CREATED.value(), response);
    }

    @PostMapping("/many-to-many/id/{relationId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ManyToManyInsertRequest> insertManyToManyDataById(@PathVariable Long relationId,
                                                      @Valid @RequestBody ManyToManyInsertRequest request) {
        var response = relationService.insertManyToManyDataById(relationId, request);
        return ApiResponse.success(HttpStatus.CREATED.value(), response);
    }

    @PostMapping("/many-to-many/name/{tableName}")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ManyToManyInsertRequest> insertManyToManyDataByName(@PathVariable String tableName,
                                                        @Valid @RequestBody ManyToManyInsertRequest request) {
        var response = relationService.insertManyToManyDataByName(tableName, request);
        return ApiResponse.success(HttpStatus.CREATED.value(), response);
    }

    @DeleteMapping("/many-to-many/id/{relationId}")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> deleteManyToManyDataById(@PathVariable Long relationId,
                                                      @Valid @RequestBody ManyToManyInsertRequest request) {
        relationService.deleteManyToManyDataById(relationId, request);
        return ApiResponse.success(HttpStatus.OK.value(), null);
    }

    @DeleteMapping("/many-to-many/name/{tableName}")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> deleteManyToManyDataByName(@PathVariable String tableName,
                                                        @Valid @RequestBody ManyToManyInsertRequest request) {
        relationService.deleteManyToManyDataByName(tableName, request);
        return ApiResponse.success(HttpStatus.OK.value(), null);
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<List<RelationResponse>> getAllRelations() {
        var response = relationService.getAllRelations();
        return ApiResponse.success(HttpStatus.OK.value(), response);
    }
}
