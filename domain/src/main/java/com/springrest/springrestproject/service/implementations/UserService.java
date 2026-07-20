package com.springrest.springrestproject.service.implementations;

import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.core.exception.FieldValidationError;
import java.util.List;
import com.springrest.springrestproject.dto.request.user.UserRequest;
import com.springrest.springrestproject.dto.response.user.GroupResponse;
import com.springrest.springrestproject.dto.response.user.UserResponse;
import com.springrest.springrestproject.model.user.AppUser;
import com.springrest.springrestproject.model.user.GroupName;
import com.springrest.springrestproject.model.user.Role;
import com.springrest.springrestproject.model.user.UserGroup;
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
        if (user.role() == null || user.role().equals(Role.MCP_AGENT)) {
            throw new ApplicationException(
                    ErrorCode.BAD_REQUEST,
                    List.of(new FieldValidationError("role", "Invalid user role")),
                    "Invalid user role"
            );
        }
        AppUser userToSave = AppUser.builder()
                .username(user.username())
                .password(passwordEncoder.encode(user.password()))
                .role(user.role())
                .active(true)
                .build();
        AppUser savedUser = userRepo.save(userToSave);
        userRepo.insertRegisteredUserGroup(savedUser.id(), userId);
        String simulatedSql = String.format(
                "INSERT INTO app_user (id, username, role, active) VALUES (%d, '%s', '%s', true);",
                savedUser.id(),
                savedUser.username(),
                savedUser.role().name()
        );
        metadataService.logSchemaChange("app_user", simulatedSql, userId);
        return new UserRequest(
                savedUser.id(),
                savedUser.username(),
                savedUser.role(),
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
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_FOUND, "ID: " + id));
        return new UserRequest(
                user.id(),
                user.username(),
                user.role(),
                "********"
        );
    }

    @Override
    @Transactional(readOnly = true)
    public UserRequest getUserByName(String name) {
        AppUser user = userRepo.findByUsername(name)
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_FOUND, name));
        return new UserRequest(
                user.id(),
                user.username(),
                user.role(),
                "********"
        );
    }

    @Override
    @Transactional(readOnly = true)
    public AppUser findByUsername(String username) {
        return userRepo.findByUsername(username)
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_FOUND, username));
    }

    @Override
    @Transactional
    public void deleteUserById(Long id, Long userId) {
        AppUser user = userRepo.findById(id)
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_FOUND, "ID: " + id));
        if(!Boolean.TRUE.equals(user.active())){
            throw new ApplicationException(
                    ErrorCode.ALREADY_DELETED,
                    List.of(new FieldValidationError("id", "User is already inactive/deleted"))
            );
        }
        AppUser updatedUser = AppUser.builder()
                .id(user.id())
                .username(user.username())
                .password(user.password())
                .role(user.role())
                .active(false)
                .build();
        String simulatedSql = String.format("UPDATE app_users SET active = false WHERE id = %d;", id);
        metadataService.logSchemaChange("app_users", simulatedSql, userId);
        userRepo.save(updatedUser);
    }

    @Override
    @Transactional
    public GroupResponse addGroupToUser(Long userId, GroupName groupName, Long executorId) {
        userRepo.findById(userId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_FOUND, "ID: " + userId));
        if (groupName == null) {
            throw new ApplicationException(
                    ErrorCode.INVALID_GROUP_NAME,
                    List.of(new FieldValidationError("groupName", "Group name must not be null")),
                    "null"
            );
        }
        if (groupName == GroupName.REGISTERED_USER) {
            throw new ApplicationException(
                    ErrorCode.SYSTEM_MANAGED_GROUP,
                    List.of(new FieldValidationError("groupName", "registered_user is system-managed and cannot be assigned or removed manually"))
            );
        }
        if (userRepo.existsByUserIdAndGroupName(userId, groupName)) {
            throw new ApplicationException(
                    ErrorCode.GROUP_ALREADY_EXISTS,
                    List.of(new FieldValidationError("groupName", "User already has this group assigned"))
            );
        }
        UserGroup saved = userRepo.saveGroup(userId, groupName, executorId);
        return toGroupResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupResponse> getGroupsForUser(Long userId) {
        userRepo.findById(userId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_FOUND, "ID: " + userId));
        return userRepo.findGroupsByUserId(userId).stream()
                .map(this::toGroupResponse)
                .toList();
    }

    @Override
    @Transactional
    public void removeGroupById(Long userId, Long groupId, Long executorId) {
        UserGroup group = userRepo.findGroupById(groupId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.GROUP_NOT_FOUND, "ID: " + groupId));
        if (!group.userId().equals(userId)) {
            throw new ApplicationException(ErrorCode.GROUP_NOT_FOUND, "ID: " + groupId);
        }
        if (group.groupName() == GroupName.REGISTERED_USER) {
            throw new ApplicationException(
                    ErrorCode.SYSTEM_MANAGED_GROUP,
                    List.of(new FieldValidationError("groupId", "registered_user is system-managed and cannot be assigned or removed manually"))
            );
        }
        userRepo.deleteGroup(group, executorId);
    }

    @Override
    @Transactional
    public void removeGroupByName(Long userId, String groupNameRaw, Long executorId) {
        GroupName groupName;
        try {
            groupName = GroupName.valueOf(groupNameRaw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApplicationException(ErrorCode.INVALID_GROUP_NAME, groupNameRaw);
        }
        UserGroup group = userRepo.findGroupByUserIdAndName(userId, groupName)
                .orElseThrow(() -> new ApplicationException(ErrorCode.GROUP_NOT_FOUND, "user " + userId + ", group " + groupName));
        if (groupName == GroupName.REGISTERED_USER) {
            throw new ApplicationException(
                    ErrorCode.SYSTEM_MANAGED_GROUP,
                    List.of(new FieldValidationError("groupName", "registered_user is system-managed and cannot be assigned or removed manually"))
            );
        }
        userRepo.deleteGroup(group, executorId);
    }

    private GroupResponse toGroupResponse(UserGroup group) {
        return new GroupResponse(group.id(), group.userId(), group.groupName(), group.createdDate());
    }
}