/*
 * Copyright (C) 2026 Mitch Trachtenberg — GPL v3
 */
package com.mjtrac.counter.service;

import com.mjtrac.counter.entity.CounterUser;
import com.mjtrac.counter.repository.CounterUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;

/**
 * User management (create, password change, role grants). Login credential
 * verification is done directly by the FX login screen via findByUsername() +
 * PasswordEncoder, the same pattern blScanner/blBuilder use — this class no
 * longer implements Spring Security's UserDetailsService, since the counter
 * workflow's login no longer runs through a servlet form-login filter chain.
 * (The Viewer screen's embedded web server still uses Spring Security, but
 * ViewerSecurityConfig is simplified to permit-all — see its class comment.)
 */
@Service
public class CounterUserService {

    private final CounterUserRepository userRepo;
    private final PasswordEncoder       passwordEncoder;

    public CounterUserService(CounterUserRepository userRepo, PasswordEncoder passwordEncoder) {
        this.userRepo        = userRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public CounterUser createUser(String username, String rawPassword, Set<CounterUser.Role> roles) {
        if (userRepo.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists: " + username);
        }
        CounterUser user = new CounterUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRoles(roles);
        user.setEnabled(true);
        return userRepo.save(user);
    }

    public Optional<CounterUser> findByUsername(String username) {
        return userRepo.findByUsername(username);
    }

    /** Admin-driven password reset — no current-password check. */
    @Transactional
    public void changePassword(Long userId, String newRawPassword) {
        CounterUser user = userRepo.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.setPasswordHash(passwordEncoder.encode(newRawPassword));
        userRepo.save(user);
    }

    /**
     * Self-service password change — verifies current password before updating.
     * Returns true on success, false if currentPassword is incorrect.
     */
    @Transactional
    public boolean changeOwnPassword(Long userId, String currentRawPassword, String newRawPassword) {
        CounterUser user = userRepo.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        if (!passwordEncoder.matches(currentRawPassword, user.getPasswordHash())) {
            return false;
        }
        user.setPasswordHash(passwordEncoder.encode(newRawPassword));
        userRepo.save(user);
        return true;
    }

    @Transactional
    public void setEnabled(Long userId, boolean enabled) {
        CounterUser user = userRepo.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.setEnabled(enabled);
        userRepo.save(user);
    }

    /** Grants/denies roles by replacing a user's role set outright. */
    @Transactional
    public void updateRoles(Long userId, Set<CounterUser.Role> roles) {
        CounterUser user = userRepo.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.setRoles(roles);
        userRepo.save(user);
    }
}
