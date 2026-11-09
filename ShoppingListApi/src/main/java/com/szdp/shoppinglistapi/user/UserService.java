package com.szdp.shoppinglistapi.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class UserService {
    private static final String RECOVERY_KEY_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int RECOVERY_KEY_LENGTH = 16;
    private static final long TOKEN_LIFETIME_HOURS = 24;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final UserSessionRepository sessions;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public UserService(
            UserRepository users,
            UserSessionRepository sessions,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService
    ) {
        this.users = users;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional
    public AuthenticationResult register(String username, String password) {
        if (users.existsByUsername(username)) {
            throw new UsernameAlreadyExistsException();
        }
        String recoveryKey = generateRecoveryKey();
        User user = users.save(new User(
                username,
                passwordEncoder.encode(password),
                hashRecoveryKey(recoveryKey)
        ));
        return issueAuthentication(user, recoveryKey);
    }

    @Transactional
    public AuthenticationResult login(String username, String password) {
        User user = users.findByUsername(username)
                .filter(candidate -> passwordEncoder.matches(password, candidate.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);
        return issueAuthentication(user, null);
    }

    @Transactional(readOnly = true)
    public User findByUsername(String username) {
        return users.findByUsername(username).orElseThrow(InvalidCredentialsException::new);
    }

    @Transactional
    public void logout(String token) {
        sessions.deleteByToken(token);
    }

    @Transactional
    public void resetPassword(String username, String recoveryKey, String newPassword) {
        Optional<User> userResult = users.findByUsername(username);
        if (userResult.isEmpty() || !matchesRecoveryKey(recoveryKey, userResult.get().getRecoveryKeyHash())) {
            throw new InvalidRecoveryCredentialsException();
        }
        User user = userResult.get();
        user.updatePasswordHash(passwordEncoder.encode(newPassword));
        sessions.deleteAllByUser_Id(user.getId());
    }

    private AuthenticationResult issueAuthentication(User user, String recoveryKey) {
        Instant expiresAt = Instant.now().plus(TOKEN_LIFETIME_HOURS, ChronoUnit.HOURS);
        String token = jwtTokenService.createToken(user.getUsername(), expiresAt);
        sessions.save(new UserSession(user, token, expiresAt));
        return new AuthenticationResult(user, token, expiresAt, recoveryKey);
    }

    private static String generateRecoveryKey() {
        StringBuilder key = new StringBuilder(RECOVERY_KEY_LENGTH);
        for (int i = 0; i < RECOVERY_KEY_LENGTH; i++) {
            key.append(RECOVERY_KEY_CHARACTERS.charAt(RANDOM.nextInt(RECOVERY_KEY_CHARACTERS.length())));
        }
        return key.toString();
    }

    private static String hashRecoveryKey(String recoveryKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(recoveryKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static boolean matchesRecoveryKey(String recoveryKey, String storedHash) {
        byte[] submittedHash = hashRecoveryKey(recoveryKey).getBytes(StandardCharsets.US_ASCII);
        byte[] expectedHash = storedHash.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(submittedHash, expectedHash);
    }

    public record AuthenticationResult(User user, String token, Instant expiresAt, String recoveryKey) {
    }
}
