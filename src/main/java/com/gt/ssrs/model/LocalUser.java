package com.gt.ssrs.model;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;

public class LocalUser implements UserDetails {

    private final Long userId;
    private final String username;
    private final String password;
    private final Instant accountExpiration;
    private final Instant credentialExpiration;
    private final Boolean locked;
    private final Boolean enabled;

    public LocalUser(Long userId, String username, String password, Instant accountExpiration, Instant credentialExpiration, Boolean locked, Boolean enabled) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.accountExpiration = accountExpiration;
        this.credentialExpiration = credentialExpiration;
        this.locked = locked;
        this.enabled = enabled;
    }

    public static LocalUser fromUserDetails(UserDetails userDetails) {
        return new LocalUser(null, userDetails.getUsername(), userDetails.getPassword(), null, null, userDetails.isAccountNonLocked() ? false : true, userDetails.isEnabled() ? true : false);
    }

    public static LocalUser newUserWithCredentials(String username, String password) {
        return new LocalUser(null, username, password, null, null, false, true);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return null;
    }

    public long getUserId() {
        return userId;
    }

    public Instant getAccountExpiration() {
        return accountExpiration;
    }

    public Instant getCredentialExpiration() {
        return  credentialExpiration;
    }

    public Boolean getLocked() {
        return locked;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public boolean isAccountNonExpired() {
        return accountExpiration == null || accountExpiration.isAfter(Instant.now());
    }

    @Override
    public boolean isAccountNonLocked() {
        return locked != true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return credentialExpiration == null || credentialExpiration.isAfter(Instant.now());
    }

    @Override
    public boolean isEnabled() {
        return enabled == true;
    }
}
