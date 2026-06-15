package com.springrest.springrestproject.service.interfaces;

import com.springrest.springrestproject.dto.response.user.UserResponse;
import com.springrest.springrestproject.model.AppUser;
import java.util.List;

public interface IUserService {
    UserResponse createUser(AppUser user);
    List<AppUser> getAllUsers();
}