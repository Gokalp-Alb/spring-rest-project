package com.springrest.springrestproject.security;

import com.springrest.springrestproject.model.AppUser;
import lombok.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.List;

public record CustomUserDetails(AppUser appUser) implements UserDetails {

    @Override
    @NonNull
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + appUser.getRole().name()));
    }

    @Override
    public String getPassword() { return appUser.getPassword(); }

    @Override
    @NonNull
    public String getUsername() { return appUser.getUsername(); }

    public Long getId() {return appUser.getId(); }

    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return  appUser.isActive(); }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return appUser.isActive(); }
}