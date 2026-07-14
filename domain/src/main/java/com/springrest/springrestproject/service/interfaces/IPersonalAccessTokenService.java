package com.springrest.springrestproject.service.interfaces;

public interface IPersonalAccessTokenService {
    String createToken(String username, String password, int expirationDays, String tokenName);
    Long validateTokenAndGetUserId(String token);
}
