package com.springrest.springrestproject.service.interfaces.ReadServices;

import com.springrest.springrestproject.dto.request.user.UserRequest;
import com.springrest.springrestproject.dto.response.user.GroupResponse;
import com.springrest.springrestproject.dto.response.user.UserResponse;
import com.springrest.springrestproject.model.user.AppUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface IUserReadService {
    Page<UserResponse> getAllUsers(Pageable pageable);
    UserRequest getUserById(Long id);
    UserRequest getUserByName(String name);
    AppUser findByUsername(String username);
    List<GroupResponse> getGroupsForUser(Long userId);
}
