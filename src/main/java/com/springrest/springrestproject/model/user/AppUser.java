package com.springrest.springrestproject.model.user;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class AppUser {
    private Long id;
    private String username;
    private String password;
    private Role role;
    private Boolean active;
}