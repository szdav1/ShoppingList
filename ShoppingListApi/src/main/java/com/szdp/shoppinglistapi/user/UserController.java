package com.szdp.shoppinglistapi.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    @Operation(description = "Create an account. Copy the returned accessToken, then use Authorize to authenticate protected endpoints.")
    public ResponseEntity<AuthenticationResponse> register(@Valid @RequestBody RegistrationRequest request) {
        UserService.AuthenticationResult result = userService.register(request.username(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(result));
    }

    @PostMapping("/login")
    @Operation(description = "Log in and return an accessToken. Copy the token value (do not include the 'Bearer ' prefix) into Authorize before calling protected endpoints.")
    public AuthenticationResponse login(@Valid @RequestBody LoginRequest request) {
        return toResponse(userService.login(request.username(), request.password()));
    }

    @GetMapping("/me")
    @Operation(
            description = "Returns the current user. Requires an active JWT: log in or register, copy accessToken, then click Authorize and paste the token without the 'Bearer ' prefix.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public UserResponse currentUser(@AuthenticationPrincipal Jwt jwt) {
        return toUserResponse(userService.findByUsername(jwt.getSubject()));
    }

    @PostMapping("/logout")
    @Operation(security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        userService.logout(jwt.getTokenValue());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        userService.resetPassword(request.username(), request.recoveryKey(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    private static AuthenticationResponse toResponse(UserService.AuthenticationResult result) {
        return new AuthenticationResponse(
                toUserResponse(result.user()),
                result.token(),
                "Bearer",
                result.expiresAt(),
                result.recoveryKey()
        );
    }

    private static UserResponse toUserResponse(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.isAdmin(), user.getCreatedAt());
    }

    public record RegistrationRequest(
            @NotBlank
            @Size(min = 3, max = 30)
            @Pattern(regexp = "^[A-Za-z0-9_]+$")
            String username,
            @NotBlank @Size(min = 8) String password
    ) {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record PasswordResetRequest(
            @NotBlank String username,
            @NotBlank @Size(min = 16, max = 16) String recoveryKey,
            @NotBlank @Size(min = 8) String newPassword
    ) {
    }

    public record UserResponse(Long id, String username, boolean admin, Instant createdAt) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AuthenticationResponse(
            UserResponse user,
            String accessToken,
            String tokenType,
            Instant expiresAt,
            String recoveryKey
    ) {
    }
}
