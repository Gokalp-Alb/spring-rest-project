package com.springrest.springrestproject.service.interfaces;

import com.springrest.springrestproject.dto.request.user.UserRequest;
import com.springrest.springrestproject.dto.response.user.UserResponse;
import com.springrest.springrestproject.model.AppUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface IUserService {
    UserRequest createUser(AppUser user, Long userId);
    Page<UserResponse> getAllUsers(Pageable pageable);
    UserRequest getUserById(Long id);
    UserRequest getUserByName(String name);
    void deleteUserById(Long id, Long userId);
    AppUser findByUsername(String username);
}