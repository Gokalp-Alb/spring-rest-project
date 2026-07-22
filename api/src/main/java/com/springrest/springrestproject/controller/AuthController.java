package com.springrest.springrestproject.controller;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.core.response.ApiResponse;
import com.springrest.springrestproject.dto.request.auth.PatCreationRequest;
import com.springrest.springrestproject.dto.request.user.LoginRequest;
import com.springrest.springrestproject.model.user.AppUser;
import com.springrest.springrestproject.service.implementations.UserService;
import com.springrest.springrestproject.service.interfaces.IPersonalAccessTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final IPersonalAccessTokenService patService;
    private final com.springrest.springrestproject.repository.AppUserRepo appUserRepo;

    @Value("${app.jwt.secret}")
    private String tokenSecret;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<String>> login(@RequestBody LoginRequest loginRequest) throws Exception {
        AppUser user = userService.findByUsername(loginRequest.username());
        if (appUserRepo.existsByUserIdAndGroupName(user.id(), com.springrest.springrestproject.model.user.GroupName.MCP_AGENT)) {
            throw new ApplicationException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(loginRequest.password(), user.password())) {
            throw new ApplicationException(ErrorCode.INVALID_CREDENTIALS);
        }
        List<String> roleClaims = appUserRepo.findGroupsByUserId(user.id()).stream()
                .map(g -> "ROLE_" + g.groupName().name())
                .toList();
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(user.username())
                .issuer("com.springrest.project")
                .issueTime(new Date())
                .expirationTime(new Date(new Date().getTime() + 86400000))
                .claim("userId", user.id())
                .claim("roles", roleClaims)
                .build();
        JWSSigner signer = new MACSigner(tokenSecret.getBytes());
        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
        signedJWT.sign(signer);

        String rawToken = signedJWT.serialize();
        ApiResponse<String> apiResponse = ApiResponse.success(rawToken);
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }

    @PostMapping("/pat")
    public ResponseEntity<ApiResponse<String>> createPersonalAccessToken(@RequestBody PatCreationRequest request) {
        int expDays = request.expirationDays() != null ? request.expirationDays() : 30;
        String rawToken = patService.createToken(
                request.username(),
                request.password(),
                expDays,
                request.tokenName()
        );
        ApiResponse<String> apiResponse = ApiResponse.success(rawToken);
        return new ResponseEntity<>(apiResponse, HttpStatus.CREATED);
    }
}