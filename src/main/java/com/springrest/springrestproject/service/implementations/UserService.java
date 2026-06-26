package com.springrest.springrestproject.service.implementations;

import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.dto.request.user.UserRequest;
import com.springrest.springrestproject.dto.response.user.UserResponse;
import com.springrest.springrestproject.model.AppUser;
import com.springrest.springrestproject.repository.AppUserRepo;
import com.springrest.springrestproject.service.interfaces.IMetadataService;
import com.springrest.springrestproject.service.interfaces.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class UserService implements IUserService {

    private final AppUserRepo userRepo;
    private final PasswordEncoder passwordEncoder;
    private final IMetadataService metadataService;

    @Override
    @Transactional
    public UserRequest createUser(AppUser user, Long userId) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setActive(true);
        AppUser savedUser = userRepo.save(user);
        String simulatedSql = String.format(
                "INSERT INTO app_user (id, username, role, active) VALUES (%d, '%s', '%s', true);",
                savedUser.getId(),
                savedUser.getUsername(),
                savedUser.getRole().name()
        );
        metadataService.logSchemaChange("app_user", simulatedSql, userId);
        return new UserRequest(
                savedUser.getId(),
                savedUser.getUsername(),
                savedUser.getRole(),
                "********"
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        return userRepo.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public UserRequest getUserById(Long id) {
        AppUser user = userRepo.findById(id)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        return new UserRequest(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                "********"
        );
    }

    @Override
    @Transactional(readOnly = true)
    public UserRequest getUserByName(String name) {
        AppUser user = userRepo.findByUsername(name)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        return new UserRequest(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                "********"
        );
    }

    @Override
    @Transactional(readOnly = true)
    public AppUser findByUsername(String username) {
        return userRepo.findByUsername(username)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Override
    @Transactional
    public void deleteUserById(Long id, Long userId) {
        AppUser user = userRepo.findById(id)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        if(!user.getActive()){
            throw new ApplicationException(ErrorCode.ALREADY_DELETED);
        }
        user.setActive(false);
        String simulatedSql = String.format("UPDATE app_users SET active = false WHERE id = %d;", id);
        metadataService.logSchemaChange("app_users", simulatedSql, userId);
        userRepo.save(user);
    }
}