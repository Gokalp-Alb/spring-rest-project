package com.springrest.springrestproject.service.implementations;

import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.model.auth.PersonalAccessToken;
import com.springrest.springrestproject.model.user.AppUser;
import com.springrest.springrestproject.model.user.Role;
import com.springrest.springrestproject.repository.AppUserRepo;
import com.springrest.springrestproject.repository.PersonalAccessTokenRepo;
import com.springrest.springrestproject.service.interfaces.IPersonalAccessTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PersonalAccessTokenService implements IPersonalAccessTokenService {
    private final AppUserRepo appUserRepo;
    private final PersonalAccessTokenRepo patRepo;
    private final PasswordEncoder passwordEncoder;

    @Override
    public String createToken(String username, String password, int expirationDays, String tokenName) {
        AppUser user = appUserRepo.findByUsername(username)
                .orElseThrow(() -> new ApplicationException(ErrorCode.INVALID_CREDENTIALS));

        if (user.role() == Role.MCP_AGENT) {
            throw new ApplicationException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (!passwordEncoder.matches(password, user.password())) {
            throw new ApplicationException(ErrorCode.INVALID_CREDENTIALS);
        }

        patRepo.deleteByUserId(user.id());

        String rawToken = "pat_" + UUID.randomUUID().toString().replace("-", "");
        String hash = hashToken(rawToken);

        PersonalAccessToken pat = new PersonalAccessToken(
                null,
                hash,
                tokenName != null && !tokenName.trim().isEmpty() ? tokenName : "default_mcp_pat",
                user.id(),
                LocalDateTime.now().plusDays(expirationDays),
                LocalDateTime.now(),
                null
        );

        patRepo.save(pat);
        return rawToken;
    }

    @Override
    public Long validateTokenAndGetUserId(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new ApplicationException(ErrorCode.UNAUTHORIZED_ACCESS, "Token is missing, expired, or invalid.");
        }

        String hash = hashToken(token.trim());
        PersonalAccessToken pat = patRepo.findByTokenHash(hash)
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHORIZED_ACCESS, "Token is missing, expired, or invalid."));

        if (pat.expiresAt().isBefore(LocalDateTime.now())) {
            throw new ApplicationException(ErrorCode.UNAUTHORIZED_ACCESS, "Token has expired.");
        }

        patRepo.updateLastUsedAt(pat.id(), LocalDateTime.now());
        return pat.userId();
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash Personal Access Token", e);
        }
    }
}
