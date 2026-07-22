package com.springrest.springrestproject.repository;

import com.springrest.springrestproject.core.governance.SystemGovernanceGuard;
import com.springrest.springrestproject.dto.response.user.UserResponse;
import com.springrest.springrestproject.model.user.AppUser;
import com.springrest.springrestproject.model.user.GroupName;
import com.springrest.springrestproject.model.user.UserGroup;
import com.springrest.springrestproject.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static jooq.generated.Tables.SYS_APP_USERS;
import static jooq.generated.Tables.SYS_APP_USERS_LOG;
import static jooq.generated.Tables.SYS_APP_USERS_SYS_USER_GROUPS_JT;
import static jooq.generated.Tables.SYS_USER_GROUPS;

@Repository
@RequiredArgsConstructor
public class AppUserRepo {
    private final DSLContext dsl;
    private final SystemGovernanceGuard governanceGuard;

    /** General-purpose lookup. Never selects PASSWORD — use {@link #findByUsernameWithPassword} for credential checks. */
    public Optional<AppUser> findByUsername(String username) {
        return dsl.select(SYS_APP_USERS.ID, SYS_APP_USERS.USERNAME, SYS_APP_USERS.ACTIVE)
                .from(SYS_APP_USERS)
                .where(SYS_APP_USERS.USERNAME.eq(username))
                .fetchOptional(this::toAppUserWithoutPassword);
    }

    /** General-purpose lookup. Never selects PASSWORD — use {@link #findByIdWithPassword} for credential checks. */
    public Optional<AppUser> findById(Long id) {
        return dsl.select(SYS_APP_USERS.ID, SYS_APP_USERS.USERNAME, SYS_APP_USERS.ACTIVE)
                .from(SYS_APP_USERS)
                .where(SYS_APP_USERS.ID.eq(id))
                .fetchOptional(this::toAppUserWithoutPassword);
    }

    private AppUser toAppUserWithoutPassword(org.jooq.Record record) {
        return AppUser.builder()
                .id(record.get(SYS_APP_USERS.ID))
                .username(record.get(SYS_APP_USERS.USERNAME))
                .active(record.get(SYS_APP_USERS.ACTIVE))
                .build();
    }

    /** Only for actual credential verification (login, PAT creation). Do not use for general reads. */
    public Optional<AppUser> findByUsernameWithPassword(String username) {
        return Optional.ofNullable(
                dsl.select(SYS_APP_USERS.ID, SYS_APP_USERS.USERNAME, SYS_APP_USERS.PASSWORD, SYS_APP_USERS.ACTIVE)
                        .from(SYS_APP_USERS)
                        .where(SYS_APP_USERS.USERNAME.eq(username))
                        .fetchOneInto(AppUser.class)
        );
    }

    /** Only for preserving the existing password hash across a non-credential update (e.g. deactivation). */
    public Optional<AppUser> findByIdWithPassword(Long id) {
        return Optional.ofNullable(
                dsl.select(SYS_APP_USERS.ID, SYS_APP_USERS.USERNAME, SYS_APP_USERS.PASSWORD, SYS_APP_USERS.ACTIVE)
                        .from(SYS_APP_USERS)
                        .where(SYS_APP_USERS.ID.eq(id))
                        .fetchOneInto(AppUser.class)
        );
    }

    public boolean existsByUsername(String username) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(SYS_APP_USERS)
                        .where(SYS_APP_USERS.USERNAME.eq(username))
        );
    }

    public boolean existsByUserId(Long id) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(SYS_APP_USERS)
                        .where(SYS_APP_USERS.ID.eq(id))
        );
    }

    public Page<UserResponse> findAll(Pageable pageable) {
        int total = dsl.fetchCount(SYS_APP_USERS);
        List<UserResponse> users = dsl
                .select(SYS_APP_USERS.ID, SYS_APP_USERS.USERNAME)
                .from(SYS_APP_USERS)
                .limit(pageable.getPageSize())
                .offset((int) pageable.getOffset())
                .fetchInto(UserResponse.class);
        return new PageImpl<>(users, pageable, total);
    }

    /** Application-facing save: new inserts always start unrestricted; updates reject touching a restricted row. */
    public AppUser save(AppUser user) {
        if (user.id() == null) {
            return insertRow(user, false, SecurityUtils.getCurrentUserId());
        }
        Boolean currentRestricted = dsl.select(SYS_APP_USERS.IS_RESTRICTED)
                .from(SYS_APP_USERS).where(SYS_APP_USERS.ID.eq(user.id())).fetchOne(SYS_APP_USERS.IS_RESTRICTED);
        governanceGuard.assertRowMutable(Boolean.TRUE.equals(currentRestricted));
        dsl.update(SYS_APP_USERS)
                .set(SYS_APP_USERS.USERNAME, user.username())
                .set(SYS_APP_USERS.PASSWORD, user.password())
                .set(SYS_APP_USERS.ACTIVE, user.active())
                .set(SYS_APP_USERS.LAST_UPDATER_ID, SecurityUtils.getCurrentUserId())
                .set(SYS_APP_USERS.LAST_CHANGED_DATE, LocalDateTime.now())
                .where(SYS_APP_USERS.ID.eq(user.id()))
                .execute();
        logAppUserMutation(user, "PUT");
        return user;
    }

    /** Internal (non-API) save used only by AdminInitializerConfig to seed the bootstrap admin with isRestricted=true. */
    public AppUser saveInternal(AppUser user, boolean isRestricted, Long systemActorId) {
        return insertRow(user, isRestricted, systemActorId);
    }

    private AppUser insertRow(AppUser user, boolean isRestricted, Long actorId) {
        Long generatedId = Objects.requireNonNull(dsl.insertInto(SYS_APP_USERS)
                        .set(SYS_APP_USERS.USERNAME, user.username())
                        .set(SYS_APP_USERS.PASSWORD, user.password())
                        .set(SYS_APP_USERS.ACTIVE, user.active())
                        .set(SYS_APP_USERS.CREATOR_ID, actorId)
                        .set(SYS_APP_USERS.CREATED_DATE, LocalDateTime.now())
                        .set(SYS_APP_USERS.LAST_UPDATER_ID, actorId)
                        .set(SYS_APP_USERS.LAST_CHANGED_DATE, LocalDateTime.now())
                        .set(SYS_APP_USERS.IS_RESTRICTED, isRestricted)
                        .returning(SYS_APP_USERS.ID)
                        .fetchOne())
                .getValue(SYS_APP_USERS.ID);
        AppUser savedUser = AppUser.builder()
                .id(generatedId)
                .username(user.username())
                .password(user.password())
                .active(user.active())
                .build();
        logAppUserMutation(savedUser, "POST");
        return savedUser;
    }

    private void logAppUserMutation(AppUser user, String operation) {
        Long executorId = SecurityUtils.getCurrentUserId();
        if (executorId == null) {
            executorId = 0L;
        }
        dsl.insertInto(SYS_APP_USERS_LOG)
                .set(SYS_APP_USERS_LOG.ID, user.id())
                .set(SYS_APP_USERS_LOG.ACTIVE, user.active())
                .set(SYS_APP_USERS_LOG.PASSWORD, "********")
                .set(SYS_APP_USERS_LOG.USERNAME, user.username())
                .set(SYS_APP_USERS_LOG.OPERATION_TYPE, operation)
                .set(SYS_APP_USERS_LOG.EXECUTED_AT, LocalDateTime.now())
                .set(SYS_APP_USERS_LOG.USER_ID, executorId)
                .execute();
    }

    public List<UserGroup> findGroupsByUserId(Long userId) {
        return dsl.select(SYS_APP_USERS_SYS_USER_GROUPS_JT.ID, SYS_APP_USERS_SYS_USER_GROUPS_JT.USER_ID,
                        SYS_USER_GROUPS.GROUP_NAME,
                        SYS_APP_USERS_SYS_USER_GROUPS_JT.CREATOR_ID, SYS_APP_USERS_SYS_USER_GROUPS_JT.CREATED_DATE,
                        SYS_APP_USERS_SYS_USER_GROUPS_JT.LAST_UPDATER_ID, SYS_APP_USERS_SYS_USER_GROUPS_JT.LAST_CHANGED_DATE)
                .from(SYS_APP_USERS_SYS_USER_GROUPS_JT)
                .join(SYS_USER_GROUPS).on(SYS_APP_USERS_SYS_USER_GROUPS_JT.GROUP_ID.eq(SYS_USER_GROUPS.ID))
                .where(SYS_APP_USERS_SYS_USER_GROUPS_JT.USER_ID.eq(userId))
                .fetch(this::toUserGroup);
    }

    public boolean existsByUserIdAndGroupName(Long userId, GroupName groupName) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(SYS_APP_USERS_SYS_USER_GROUPS_JT)
                        .join(SYS_USER_GROUPS).on(SYS_APP_USERS_SYS_USER_GROUPS_JT.GROUP_ID.eq(SYS_USER_GROUPS.ID))
                        .where(SYS_APP_USERS_SYS_USER_GROUPS_JT.USER_ID.eq(userId))
                        .and(SYS_USER_GROUPS.GROUP_NAME.eq(groupName.name()))
        );
    }

    public Optional<UserGroup> findGroupById(Long junctionId) {
        return dsl.select(SYS_APP_USERS_SYS_USER_GROUPS_JT.ID, SYS_APP_USERS_SYS_USER_GROUPS_JT.USER_ID,
                        SYS_USER_GROUPS.GROUP_NAME,
                        SYS_APP_USERS_SYS_USER_GROUPS_JT.CREATOR_ID, SYS_APP_USERS_SYS_USER_GROUPS_JT.CREATED_DATE,
                        SYS_APP_USERS_SYS_USER_GROUPS_JT.LAST_UPDATER_ID, SYS_APP_USERS_SYS_USER_GROUPS_JT.LAST_CHANGED_DATE)
                .from(SYS_APP_USERS_SYS_USER_GROUPS_JT)
                .join(SYS_USER_GROUPS).on(SYS_APP_USERS_SYS_USER_GROUPS_JT.GROUP_ID.eq(SYS_USER_GROUPS.ID))
                .where(SYS_APP_USERS_SYS_USER_GROUPS_JT.ID.eq(junctionId))
                .fetchOptional(this::toUserGroup);
    }

    public Optional<UserGroup> findGroupByUserIdAndName(Long userId, GroupName groupName) {
        return dsl.select(SYS_APP_USERS_SYS_USER_GROUPS_JT.ID, SYS_APP_USERS_SYS_USER_GROUPS_JT.USER_ID,
                        SYS_USER_GROUPS.GROUP_NAME,
                        SYS_APP_USERS_SYS_USER_GROUPS_JT.CREATOR_ID, SYS_APP_USERS_SYS_USER_GROUPS_JT.CREATED_DATE,
                        SYS_APP_USERS_SYS_USER_GROUPS_JT.LAST_UPDATER_ID, SYS_APP_USERS_SYS_USER_GROUPS_JT.LAST_CHANGED_DATE)
                .from(SYS_APP_USERS_SYS_USER_GROUPS_JT)
                .join(SYS_USER_GROUPS).on(SYS_APP_USERS_SYS_USER_GROUPS_JT.GROUP_ID.eq(SYS_USER_GROUPS.ID))
                .where(SYS_APP_USERS_SYS_USER_GROUPS_JT.USER_ID.eq(userId))
                .and(SYS_USER_GROUPS.GROUP_NAME.eq(groupName.name()))
                .fetchOptional(this::toUserGroup);
    }

    public UserGroup saveGroup(Long userId, GroupName groupName, Long executorId) {
        return insertGroupRow(userId, groupName, executorId, false);
    }

    /**
     * Internal (non-API) save used only by AdminInitializerConfig / migration-equivalent seeding.
     */
    public void saveGroupInternal(Long userId, GroupName groupName, Long systemActorId, boolean isRestricted) {
        insertGroupRow(userId, groupName, systemActorId, isRestricted);
    }

    private UserGroup insertGroupRow(Long userId, GroupName groupName, Long executorId, boolean isRestricted) {
        Long groupId = Objects.requireNonNull(dsl.select(SYS_USER_GROUPS.ID)
                .from(SYS_USER_GROUPS)
                .where(SYS_USER_GROUPS.GROUP_NAME.eq(groupName.name()))
                .fetchOne(SYS_USER_GROUPS.ID));
        Long generatedId = Objects.requireNonNull(dsl.insertInto(SYS_APP_USERS_SYS_USER_GROUPS_JT)
                        .set(SYS_APP_USERS_SYS_USER_GROUPS_JT.USER_ID, userId)
                        .set(SYS_APP_USERS_SYS_USER_GROUPS_JT.GROUP_ID, groupId)
                        .set(SYS_APP_USERS_SYS_USER_GROUPS_JT.CREATOR_ID, executorId)
                        .set(SYS_APP_USERS_SYS_USER_GROUPS_JT.CREATED_DATE, LocalDateTime.now())
                        .set(SYS_APP_USERS_SYS_USER_GROUPS_JT.LAST_UPDATER_ID, executorId)
                        .set(SYS_APP_USERS_SYS_USER_GROUPS_JT.LAST_CHANGED_DATE, LocalDateTime.now())
                        .set(SYS_APP_USERS_SYS_USER_GROUPS_JT.IS_RESTRICTED, isRestricted)
                        .returning(SYS_APP_USERS_SYS_USER_GROUPS_JT.ID)
                        .fetchOne())
                .getValue(SYS_APP_USERS_SYS_USER_GROUPS_JT.ID);
        return findGroupById(generatedId).orElseThrow();
    }

    public void deleteGroup(UserGroup group, Long executorId) {
        Boolean currentRestricted = dsl.select(SYS_APP_USERS_SYS_USER_GROUPS_JT.IS_RESTRICTED)
                .from(SYS_APP_USERS_SYS_USER_GROUPS_JT)
                .where(SYS_APP_USERS_SYS_USER_GROUPS_JT.ID.eq(group.id()))
                .fetchOne(SYS_APP_USERS_SYS_USER_GROUPS_JT.IS_RESTRICTED);
        governanceGuard.assertRowMutable(Boolean.TRUE.equals(currentRestricted));
        dsl.deleteFrom(SYS_APP_USERS_SYS_USER_GROUPS_JT)
                .where(SYS_APP_USERS_SYS_USER_GROUPS_JT.ID.eq(group.id()))
                .execute();
    }

    public void insertRegisteredUserGroup(Long userId, Long executorId) {
        saveGroup(userId, GroupName.REGISTERED_USER, executorId);
    }

    private UserGroup toUserGroup(org.jooq.Record record) {
        return new UserGroup(
                record.get(SYS_APP_USERS_SYS_USER_GROUPS_JT.ID),
                record.get(SYS_APP_USERS_SYS_USER_GROUPS_JT.USER_ID),
                GroupName.valueOf(record.get(SYS_USER_GROUPS.GROUP_NAME)),
                record.get(SYS_APP_USERS_SYS_USER_GROUPS_JT.CREATOR_ID),
                record.get(SYS_APP_USERS_SYS_USER_GROUPS_JT.CREATED_DATE),
                record.get(SYS_APP_USERS_SYS_USER_GROUPS_JT.LAST_UPDATER_ID),
                record.get(SYS_APP_USERS_SYS_USER_GROUPS_JT.LAST_CHANGED_DATE)
        );
    }

}
