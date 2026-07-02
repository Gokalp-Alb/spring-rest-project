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
import com.springrest.springrestproject.dto.request.user.LoginRequest;
import com.springrest.springrestproject.model.user.AppUser;
import com.springrest.springrestproject.service.implementations.UserService;
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

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.jwt.secret}")
    private String tokenSecret;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<String>> login(@RequestBody LoginRequest loginRequest) throws Exception {
        AppUser user = userService.findByUsername(loginRequest.username());
        if (!passwordEncoder.matches(loginRequest.password(), user.getPassword())) {
            throw new ApplicationException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(user.getUsername())
                .issuer("com.springrest.project")
                .issueTime(new Date())
                .expirationTime(new Date(new Date().getTime() + 86400000)) // 24 hours lifetime
                .claim("userId", user.getId())
                .claim("roles", "ROLE_" + user.getRole().name())
                .build();

        JWSSigner signer = new MACSigner(tokenSecret.getBytes());
        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
        signedJWT.sign(signer);

        String rawToken = signedJWT.serialize();
        ApiResponse<String> apiResponse = ApiResponse.success(rawToken);
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }
}