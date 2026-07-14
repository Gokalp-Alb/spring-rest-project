package com.springrest.springrestproject.repository;

import com.springrest.springrestproject.model.auth.PersonalAccessToken;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

import static jooq.generated.Tables.PERSONAL_ACCESS_TOKENS;

@Repository
@RequiredArgsConstructor
public class PersonalAccessTokenRepo {
    private final DSLContext dsl;

    public Optional<PersonalAccessToken> findByTokenHash(String tokenHash) {
        return Optional.ofNullable(
                dsl.selectFrom(PERSONAL_ACCESS_TOKENS)
                        .where(PERSONAL_ACCESS_TOKENS.TOKEN_HASH.eq(tokenHash))
                        .fetchOneInto(PersonalAccessToken.class)
        );
    }

    public void deleteByUserId(Long userId) {
        dsl.deleteFrom(PERSONAL_ACCESS_TOKENS)
                .where(PERSONAL_ACCESS_TOKENS.USER_ID.eq(userId))
                .execute();
    }

    public PersonalAccessToken save(PersonalAccessToken pat) {
        if (pat.id() == null) {
            Long generatedId = Objects.requireNonNull(dsl.insertInto(PERSONAL_ACCESS_TOKENS)
                            .set(PERSONAL_ACCESS_TOKENS.TOKEN_HASH, pat.tokenHash())
                            .set(PERSONAL_ACCESS_TOKENS.NAME, pat.name())
                            .set(PERSONAL_ACCESS_TOKENS.USER_ID, pat.userId())
                            .set(PERSONAL_ACCESS_TOKENS.EXPIRES_AT, pat.expiresAt())
                            .set(PERSONAL_ACCESS_TOKENS.CREATED_AT, pat.createdAt() != null ? pat.createdAt() : LocalDateTime.now())
                            .set(PERSONAL_ACCESS_TOKENS.LAST_USED_AT, pat.lastUsedAt())
                            .returning(PERSONAL_ACCESS_TOKENS.ID)
                            .fetchOne())
                    .getValue(PERSONAL_ACCESS_TOKENS.ID);
            return new PersonalAccessToken(
                    generatedId,
                    pat.tokenHash(),
                    pat.name(),
                    pat.userId(),
                    pat.expiresAt(),
                    pat.createdAt() != null ? pat.createdAt() : LocalDateTime.now(),
                    pat.lastUsedAt()
            );
        } else {
            dsl.update(PERSONAL_ACCESS_TOKENS)
                    .set(PERSONAL_ACCESS_TOKENS.TOKEN_HASH, pat.tokenHash())
                    .set(PERSONAL_ACCESS_TOKENS.NAME, pat.name())
                    .set(PERSONAL_ACCESS_TOKENS.USER_ID, pat.userId())
                    .set(PERSONAL_ACCESS_TOKENS.EXPIRES_AT, pat.expiresAt())
                    .set(PERSONAL_ACCESS_TOKENS.LAST_USED_AT, pat.lastUsedAt())
                    .where(PERSONAL_ACCESS_TOKENS.ID.eq(pat.id()))
                    .execute();
            return pat;
        }
    }

    public void updateLastUsedAt(Long id, LocalDateTime lastUsedAt) {
        dsl.update(PERSONAL_ACCESS_TOKENS)
                .set(PERSONAL_ACCESS_TOKENS.LAST_USED_AT, lastUsedAt)
                .where(PERSONAL_ACCESS_TOKENS.ID.eq(id))
                .execute();
    }
}
