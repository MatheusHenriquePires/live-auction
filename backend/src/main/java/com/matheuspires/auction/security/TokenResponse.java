package com.matheuspires.auction.security;

/** Login/refresh result. {@code expiresIn} is the access token lifetime in seconds. */
public record TokenResponse(String accessToken, String refreshToken, long expiresIn) {

}
