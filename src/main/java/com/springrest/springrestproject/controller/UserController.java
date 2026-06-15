package com.springrest.springrestproject.controller;

import com.springrest.springrestproject.core.response.ApiResponse;
import com.springrest.springrestproject.core.response.ResponseOperation;
import com.springrest.springrestproject.dto.response.user.UserResponse;
import com.springrest.springrestproject.model.AppUser;
import com.springrest.springrestproject.service.interfaces.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final IUserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> createUser(@RequestBody AppUser user) {
        UserResponse savedUser = userService.createUser(user);
        return ApiResponse.success(HttpStatus.CREATED.value(), ResponseOperation.valueOf("CREATE"), savedUser);
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<List<AppUser>> getAllUsers() {
        List<AppUser> users = userService.getAllUsers();
        return ApiResponse.success(HttpStatus.OK.value(), ResponseOperation.valueOf("READ"), users);
    }
}