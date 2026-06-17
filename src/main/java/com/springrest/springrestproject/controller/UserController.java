package com.springrest.springrestproject.controller;

import com.springrest.springrestproject.core.response.ApiResponse;
import com.springrest.springrestproject.dto.request.user.UserRequest;
import com.springrest.springrestproject.dto.response.user.UserResponse;
import com.springrest.springrestproject.model.AppUser;
import com.springrest.springrestproject.security.CustomUserDetails;
import com.springrest.springrestproject.service.interfaces.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final IUserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserRequest> createUser(@RequestBody AppUser user,
                                               @AuthenticationPrincipal CustomUserDetails userDetails) {
        UserRequest savedUser = userService.createUser(user, userDetails.getId());
        return ApiResponse.success(HttpStatus.CREATED.value(), savedUser);
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Page<UserResponse>> getAllUsers(
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "pageSize", required = false, defaultValue = "10") int pageSize) {
        Pageable pageable = PageRequest.of(page, pageSize);
        Page<UserResponse> users = userService.getAllUsers(pageable);
        return ApiResponse.success(HttpStatus.OK.value(), users);
    }

    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<UserRequest> getUserById(@PathVariable Long id) {
        UserRequest user = userService.getUserById(id);
        return ApiResponse.success(HttpStatus.OK.value(), user);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> deleteUserById(@PathVariable Long id,
                                            @AuthenticationPrincipal CustomUserDetails userDetails) {
        userService.deleteUserById(id, userDetails.getId());
        return ApiResponse.success(HttpStatus.OK.value(), null);
    }
}