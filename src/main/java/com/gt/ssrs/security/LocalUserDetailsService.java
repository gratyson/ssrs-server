package com.gt.ssrs.security;

import com.gt.ssrs.exception.InvalidUserDetailsException;
import com.gt.ssrs.model.LocalUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

//@Component
public class LocalUserDetailsService implements UserDetailsService {

    private final PasswordEncoder passwordEncoder;
    private final LocalUserDao localUserDao;

    @Autowired
    public LocalUserDetailsService(PasswordEncoder passwordEncoder, LocalUserDao localUserDao) {
        this.passwordEncoder = passwordEncoder;
        this.localUserDao = localUserDao;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        LocalUser localUser = localUserDao.getLocalUser(username);
        if (localUser == null) {
            throw new UsernameNotFoundException("User " + username + " not found");
        }

        return localUser;
    }

    public void saveNewUser(String username, String password, String reenterPassword) throws InvalidUserDetailsException {
        validateNewUser(username, password, reenterPassword);

        LocalUser localUser = new LocalUser(null, username, password, null, null, null, true);
        localUserDao.createLocalUser(localUser);
    }

    private void validateNewUser(String username, String password, String reenterPassword) throws InvalidUserDetailsException {
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
