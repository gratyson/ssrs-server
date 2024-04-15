package com.gt.ssrs.security;

import com.gt.ssrs.exception.InvalidUserDetailsException;
import com.gt.ssrs.model.LocalUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.stereotype.Component;

@Component
public class LocalUserDetailsManager implements UserDetailsManager {

    private static final Logger log = LoggerFactory.getLogger(LocalUserDetailsManager.class);

    private final PasswordEncoder passwordEncoder;
    private final LocalUserDao localUserDao;

    @Autowired
    public LocalUserDetailsManager(PasswordEncoder passwordEncoder, LocalUserDao localUserDao) {
        this.passwordEncoder = passwordEncoder;
        this.localUserDao = localUserDao;
    }

    @Override
    public void createUser(UserDetails user) {
        localUserDao.createLocalUser(LocalUser.fromUserDetails(user));
    }

    public void createUser(String username, String password, String reenterPassword) throws InvalidUserDetailsException {
        validateNewUser(username, password, reenterPassword);

        createUser(LocalUser.newUserWithCredentials(username, passwordEncoder.encode(password)));
    }

    @Override
    public void updateUser(UserDetails user) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void deleteUser(String username) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void changePassword(String oldPassword, String newPassword) {
        throw new RuntimeException("??? what user?");
    }

    @Override
    public boolean userExists(String username) {
        return localUserDao.getLocalUser(username) != null;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        LocalUser localUser = localUserDao.getLocalUser(username);
        if (localUser != null) {
            return localUser;
        }

        throw new UsernameNotFoundException("No user found with username " + username);
    }

    private void validateNewUser(String username, String password, String reenterPassword) throws InvalidUserDetailsException {
        if (username == null || username.isBlank()) {
            throw new InvalidUserDetailsException("Username is required");
        }

        if (username.length() > 255) {
            throw new InvalidUserDetailsException("Username exceeds max length");
        }

        if (password != null && !password.equals(reenterPassword)) {
            throw new InvalidUserDetailsException("Passwords do not match");
        }

        if (!validatePassword(password)) {
            throw new InvalidUserDetailsException("Password is not valid");
        }

        if (localUserDao.getLocalUser(username) != null) {
            throw new InvalidUserDetailsException("Username already exists");
        }
    }

    private boolean validatePassword(String password) {
        if (password == null) {
            return false;
        }

        if (password.length() < 1 || password.length() > 255) {
            return false;
        }

        // No password strength requirements for now
        return true;
    }
}
