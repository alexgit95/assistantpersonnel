package com.assistant.admin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminAuthenticationProvider implements AuthenticationProvider {

    private final AdminProperties properties;
    private final AdminLoginGuard loginGuard;
    private final PasswordEncoder passwordEncoder;
    private final String passwordHash;

    public AdminAuthenticationProvider(AdminProperties properties, AdminLoginGuard loginGuard,
                                       PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.loginGuard = loginGuard;
        this.passwordEncoder = passwordEncoder;
        this.passwordHash = passwordEncoder.encode(properties.password());
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        if (loginGuard.isLocked()) {
            throw new LockedException("Le compte administrateur est temporairement verrouille.");
        }

        String submittedUsername = authentication.getName();
        String submittedPassword = authentication.getCredentials() == null
                ? "" : authentication.getCredentials().toString();
        boolean validPassword = passwordEncoder.matches(submittedPassword, passwordHash);
        boolean validUsername = MessageDigest.isEqual(
                properties.username().getBytes(StandardCharsets.UTF_8),
                submittedUsername.getBytes(StandardCharsets.UTF_8));
        if (!validPassword || !validUsername) {
            loginGuard.recordFailure();
            if (loginGuard.isLocked()) {
                throw new LockedException("Le compte administrateur est temporairement verrouille.");
            }
            throw new BadCredentialsException("Identifiants administrateur invalides.");
        }

        loginGuard.recordSuccess();
        return UsernamePasswordAuthenticationToken.authenticated(properties.username(), null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
