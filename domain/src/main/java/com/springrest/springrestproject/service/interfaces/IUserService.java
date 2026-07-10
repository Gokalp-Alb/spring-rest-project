package com.springrest.springrestproject.service.interfaces;

import com.springrest.springrestproject.dto.request.user.UserRequest;
import com.springrest.springrestproject.model.user.AppUser;
import com.springrest.springrestproject.service.interfaces.ReadServices.IUserReadService;

public interface IUserService extends IUserReadService {
    UserRequest createUser(AppUser user, Long userId);
    void deleteUserById(Long id, Long userId);
}