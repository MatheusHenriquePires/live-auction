package com.matheuspires.auction.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.WWW_AUTHENTICATE;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.jayway.jsonpath.JsonPath;
import com.matheuspires.auction.IntegrationTest;
import com.matheuspires.auction.common.error.ApiException;
import com.matheuspires.auction.user.Role;
import com.matheuspires.auction.user.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AuthFlowTest extends IntegrationTest {

	@Autowired
	AuthService authService;

	@Autowired
	RefreshTokenRepository refreshTokens;

	@Test
	void registerLoginAndReadTheProfile() throws Exception {
		mvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON).content("""
				{"email": "Ana@Example.com", "displayName": "Ana", "password": "s3cret-pass"}
				"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.email").value("ana@example.com"))
			.andExpect(jsonPath("$.role").value("USER"))
			.andExpect(jsonPath("$.passwordHash").doesNotExist());

		String tokens = login("ana@example.com", "s3cret-pass");
		assertThat(((Number) JsonPath.read(tokens, "$.expiresIn")).longValue()).isEqualTo(900);

		mvc.perform(get("/api/v1/users/me").header(AUTHORIZATION, "Bearer " + JsonPath.read(tokens, "$.accessToken")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.displayName").value("Ana"))
			.andExpect(jsonPath("$.email").value("ana@example.com"));
	}

	@Test
	void duplicateEmailIsRejectedIgnoringCase() throws Exception {
		createUser("Bruno", Role.USER);

		mvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON).content("""
				{"email": "BRUNO@example.com", "displayName": "Bruno Two", "password": "another-pass"}
				"""))
			.andExpect(status().isConflict())
			.andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
			.andExpect(jsonPath("$.traceId").isNotEmpty());
	}

	@Test
	void registrationPayloadIsValidated() throws Exception {
		mvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON).content("""
				{"email": "not-an-email", "displayName": "A", "password": "short"}
				"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("email", "displayName", "password")));
	}

	@Test
	void wrongPasswordAndUnknownEmailGetTheSameAnswer() throws Exception {
		createUser("Carla", Role.USER);
		for (String body : List.of("""
				{"email": "carla@example.com", "password": "wrong-password"}
				""", """
				{"email": "nobody@example.com", "password": "wrong-password"}
				""")) {
			mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON).content(body))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.detail").value("Invalid e-mail or password"));
		}
	}

	@Test
	void missingTokenIsAProblemThatEchoesTheTraceId() throws Exception {
		mvc.perform(get("/api/v1/users/me").header("X-Request-Id", "trace-abc-123"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(WWW_AUTHENTICATE, "Bearer"))
			.andExpect(header().string("X-Request-Id", "trace-abc-123"))
			.andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
			.andExpect(jsonPath("$.traceId").value("trace-abc-123"));
	}

	@Test
	void forgedTokenIsRejected() throws Exception {
		mvc.perform(get("/api/v1/users/me").header(AUTHORIZATION, "Bearer eyJhbGciOiJIUzI1NiJ9.e30.forged"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void refreshRotatesAndReusingAnOldTokenRevokesEverySession() throws Exception {
		createUser("Diego", Role.USER);
		String first = JsonPath.read(login("diego@example.com", PASSWORD), "$.refreshToken");

		String second = JsonPath.read(refresh(first, 200), "$.refreshToken");
		assertThat(second).isNotEqualTo(first);

		refresh(first, 401);
		refresh(second, 401);
	}

	@Test
	void logoutRevokesTheRefreshToken() throws Exception {
		createUser("Elena", Role.USER);
		String token = JsonPath.read(login("elena@example.com", PASSWORD), "$.refreshToken");

		mvc.perform(post("/api/v1/auth/logout").contentType(APPLICATION_JSON).content(refreshBody(token)))
			.andExpect(status().isNoContent());

		refresh(token, 401);
	}

	@Test
	void concurrentRefreshesOfTheSameTokenSucceedExactlyOnce() throws Exception {
		UserAccount user = createUser("Fabio", Role.USER);
		String token = authService.login(user.getEmail(), PASSWORD).refreshToken();
		int threads = 10;
		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger successes = new AtomicInteger();
		AtomicInteger rejections = new AtomicInteger();

		try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
			List<Future<?>> results = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				results.add(pool.submit(() -> {
					start.await();
					try {
						authService.refresh(token);
						successes.incrementAndGet();
					}
					catch (ApiException ex) {
						rejections.incrementAndGet();
					}
					return null;
				}));
			}
			start.countDown();
			for (Future<?> result : results) {
				result.get(30, TimeUnit.SECONDS);
			}
		}

		assertThat(successes).hasValue(1);
		assertThat(rejections).hasValue(threads - 1);
		// The losers presented an already-rotated token, so reuse detection revoked the winner's new one too.
		assertThat(refreshTokens.countByUserIdAndRevokedFalse(user.getId())).isZero();
	}

	private String login(String email, String password) throws Exception {
		return mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON).content("""
				{"email": "%s", "password": "%s"}
				""".formatted(email, password)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
	}

	private String refresh(String refreshToken, int expectedStatus) throws Exception {
		return mvc.perform(post("/api/v1/auth/refresh").contentType(APPLICATION_JSON).content(refreshBody(refreshToken)))
			.andExpect(status().is(expectedStatus))
			.andReturn()
			.getResponse()
			.getContentAsString();
	}

	private static String refreshBody(String refreshToken) {
		return """
				{"refreshToken": "%s"}
				""".formatted(refreshToken);
	}

}
