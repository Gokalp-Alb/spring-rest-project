package com.springrest.springrestproject.controller;

import com.springrest.springrestproject.core.response.ApiResponse;
import com.springrest.springrestproject.core.response.ResponseOperation;
import com.springrest.springrestproject.dto.request.data.TableInsertRequest;
import com.springrest.springrestproject.security.CustomUserDetails;
import com.springrest.springrestproject.service.interfaces.IDataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/data")
@RequiredArgsConstructor
public class DataController {

    private final IDataService dataService;

    @PostMapping("/insert")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> insert(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody TableInsertRequest request) {

        dataService.insertRow(request, userDetails.getId());

        return ApiResponse.success(HttpStatus.CREATED.value(), ResponseOperation.CREATE, null);
    }
}