package com.springrest.springrestproject.repository;

import com.springrest.springrestproject.dto.response.user.UserResponse;
import com.springrest.springrestproject.model.AppUser;
import com.springrest.springrestproject.model.Role;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static jooq.generated.Tables.APP_USERS;
import static jooq.generated.Tables.APP_USERS_LOG;

@Repository
@RequiredArgsConstructor
public class AppUserRepo {
    private final DSLContext dsl;

    public Optional<AppUser> findByUsername(String username) {
        return Optional.ofNullable(
                dsl.selectFrom(APP_USERS)
                        .where(APP_USERS.USERNAME.eq(username))
                        .fetchOneInto(AppUser.class)
        );
    }

    public Optional<AppUser> findById(Long id) {
        return Optional.ofNullable(
                dsl.selectFrom(APP_USERS)
                        .where(APP_USERS.ID.eq(id))
                        .fetchOneInto(AppUser.class)
        );
    }

    public boolean existsByRole(Role role) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(APP_USERS)
                        .where(APP_USERS.ROLE.eq(role.name()))
        );
    }

    public Page<UserResponse> findAll(Pageable pageable) {
        int total = dsl.fetchCount(APP_USERS);
        List<UserResponse> users = dsl
                .select(APP_USERS.ID,
                        APP_USERS.USERNAME,
                        APP_USERS.ROLE)
                .from(APP_USERS)
                .limit(pageable.getPageSize())
                .offset((int) pageable.getOffset())
                .fetchInto(UserResponse.class);
        return new PageImpl<>(users, pageable, total);
    }

    public AppUser save(AppUser user) {
        if (user.getId() == null) {
            Long generatedId = Objects.requireNonNull(dsl.insertInto(APP_USERS)
                            .set(APP_USERS.USERNAME, user.getUsername())
                            .set(APP_USERS.PASSWORD, user.getPassword())
                            .set(APP_USERS.ROLE, user.getRole() != null ? user.getRole().name() : null)
                            .set(APP_USERS.ACTIVE, user.getActive())
                            .returning(APP_USERS.ID)
                            .fetchOne())
                    .getValue(APP_USERS.ID);
            user.setId(generatedId);
            logAppUserMutation(user, "POST");
        } else {
            dsl.update(APP_USERS)
                    .set(APP_USERS.USERNAME, user.getUsername())
                    .set(APP_USERS.PASSWORD, user.getPassword())
                    .set(APP_USERS.ROLE, user.getRole() != null ? user.getRole().name() : null)
                    .set(APP_USERS.ACTIVE, user.getActive())
                    .where(APP_USERS.ID.eq(user.getId()))
                    .execute();
            logAppUserMutation(user, "PUT");
        }
        return user;
    }

    private void logAppUserMutation(AppUser user, String operation) {
        Long executorId = com.springrest.springrestproject.util.SecurityUtils.getCurrentUserId();
        if (executorId == null) {
            executorId = 0L;
        }
        dsl.insertInto(APP_USERS_LOG)
                .set(APP_USERS_LOG.ID, user.getId())
                .set(APP_USERS_LOG.ACTIVE, user.getActive())
                .set(APP_USERS_LOG.PASSWORD, user.getPassword())
                .set(APP_USERS_LOG.ROLE, user.getRole() != null ? user.getRole().name() : null)
                .set(APP_USERS_LOG.USERNAME, user.getUsername())
                .set(APP_USERS_LOG.OPERATION_TYPE, operation)
                .set(APP_USERS_LOG.EXECUTED_AT, java.time.LocalDateTime.now())
                .set(APP_USERS_LOG.USER_ID, executorId)
                .execute();
    }
}