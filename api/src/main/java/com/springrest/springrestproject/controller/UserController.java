package com.springrest.springrestproject.controller;

import com.springrest.springrestproject.core.response.ApiResponse;
import com.springrest.springrestproject.dto.request.user.GroupRequest;
import com.springrest.springrestproject.dto.request.user.UserRequest;
import com.springrest.springrestproject.dto.response.user.GroupResponse;
import com.springrest.springrestproject.dto.response.user.UserResponse;
import com.springrest.springrestproject.model.user.AppUser;
import com.springrest.springrestproject.service.interfaces.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final IUserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserRequest> createUser(@RequestBody AppUser user,
                                               @AuthenticationPrincipal Jwt jwt) {
        UserRequest savedUser = userService.createUser(user, jwt.getClaim("userId"));
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

    @GetMapping("/id/{id}")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<UserRequest> getUserById(@PathVariable Long id) {
        UserRequest user = userService.getUserById(id);
        return ApiResponse.success(HttpStatus.OK.value(), user);
    }

    @GetMapping("/name/{name}")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<UserRequest> getUserByName(@PathVariable String name) {
        UserRequest user = userService.getUserByName(name);
        return ApiResponse.success(HttpStatus.OK.value(), user);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> deleteUserById(@PathVariable Long id,
                                            @AuthenticationPrincipal Jwt jwt) {
        userService.deleteUserById(id, jwt.getClaim("userId"));
        return ApiResponse.success(HttpStatus.OK.value(), null);
    }

    @PostMapping("/{userId}/groups")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GroupResponse> addGroupToUser(@PathVariable Long userId,
                                                      @RequestBody GroupRequest request,
                                                      @AuthenticationPrincipal Jwt jwt) {
        GroupResponse group = userService.addGroupToUser(userId, request.groupName(), jwt.getClaim("userId"));
        return ApiResponse.success(HttpStatus.CREATED.value(), group);
    }

    @GetMapping("/{userId}/groups")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<List<GroupResponse>> getGroupsForUser(@PathVariable Long userId) {
        List<GroupResponse> groups = userService.getGroupsForUser(userId);
        return ApiResponse.success(HttpStatus.OK.value(), groups);
    }

    @DeleteMapping("/{userId}/groups/id/{groupId}")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> removeGroupById(@PathVariable Long userId,
                                              @PathVariable Long groupId,
                                              @AuthenticationPrincipal Jwt jwt) {
        userService.removeGroupById(userId, groupId, jwt.getClaim("userId"));
        return ApiResponse.success(HttpStatus.OK.value(), null);
    }

    @DeleteMapping("/{userId}/groups/name/{groupName}")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> removeGroupByName(@PathVariable Long userId,
                                                @PathVariable String groupName,
                                                @AuthenticationPrincipal Jwt jwt) {
        userService.removeGroupByName(userId, groupName, jwt.getClaim("userId"));
        return ApiResponse.success(HttpStatus.OK.value(), null);
    }
}