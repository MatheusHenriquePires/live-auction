package com.matheuspires.auction.security;

import com.matheuspires.auction.user.UserResponse;
import com.matheuspires.auction.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Registration, login and refresh-token rotation")
class AuthController {

	private final UserService userService;

	private final AuthService authService;

	AuthController(UserService userService, AuthService authService) {
		this.userService = userService;
		this.authService = authService;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create an account")
	UserResponse register(@Valid @RequestBody RegisterRequest request) {
		return UserResponse.from(userService.register(request.email(), request.displayName(), request.password()));
	}

	@PostMapping("/login")
	@Operation(summary = "Exchange e-mail and password for an access token and a refresh token")
	TokenResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request.email(), request.password());
	}

	@PostMapping("/refresh")
	@Operation(summary = "Rotate a refresh token; reusing an old one revokes all of the user's sessions")
	TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
		return authService.refresh(request.refreshToken());
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Revoke a refresh token")
	void logout(@Valid @RequestBody RefreshTokenRequest request) {
		authService.logout(request.refreshToken());
	}

	record RegisterRequest(@NotBlank @Email @Size(max = 255) String email,
			@NotBlank @Size(min = 2, max = 60) String displayName, @NotBlank @Size(min = 8, max = 72) String password) {
	}

	record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
	}

	record RefreshTokenRequest(@NotBlank @Size(max = 200) String refreshToken) {
	}

}
