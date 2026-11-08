package com.matheuspires.auction.security;

import java.time.Duration;
import java.util.List;

import com.matheuspires.auction.user.Role;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless JWT security. Access tokens are validated by Spring's OAuth2 resource-server filter
 * (signature, expiry and issuer); no session, no CSRF (no cookies are used).
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

	private static final String[] PUBLIC_DOCS = { "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs",
			"/v3/api-docs/**" };

	@Bean
	SecurityFilterChain apiSecurity(HttpSecurity http, SecurityProblemHandler problemHandler,
			JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
		http.csrf(AbstractHttpConfigurer::disable)
			.cors(Customizer.withDefaults())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth.requestMatchers("/api/v1/auth/**")
				.permitAll()
				.requestMatchers(HttpMethod.GET, "/api/v1/auctions", "/api/v1/auctions/**", "/api/v1/time")
				.permitAll()
				.requestMatchers(PUBLIC_DOCS)
				.permitAll()
				.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
				.permitAll()
				.requestMatchers("/actuator/**")
				.hasRole(Role.ADMIN.name())
				.requestMatchers("/error")
				.permitAll()
				.anyRequest()
				.authenticated())
			.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
				.authenticationEntryPoint(problemHandler)
				.accessDeniedHandler(problemHandler))
			.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(problemHandler)
				.accessDeniedHandler(problemHandler));
		return http.build();
	}

	@Bean
	JwtDecoder jwtDecoder(AuctionSecurityProperties properties) {
		return JwtCodecs.decoder(properties.jwt().secret(), properties.jwt().issuer());
	}

	@Bean
	JwtEncoder jwtEncoder(AuctionSecurityProperties properties) {
		return JwtCodecs.encoder(properties.jwt().secret());
	}

	/** Maps the {@code role} claim to a {@code ROLE_*} authority; the principal name is the {@code sub} claim. */
	@Bean
	JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName(TokenService.ROLE_CLAIM);
		authorities.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}

	@Bean
	PasswordEncoder passwordEncoder(AuctionSecurityProperties properties) {
		return new BCryptPasswordEncoder(properties.bcryptStrength());
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(AuctionSecurityProperties properties) {
		CorsConfiguration cors = new CorsConfiguration();
		cors.setAllowedOrigins(properties.corsAllowedOrigins());
		cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id"));
		cors.setExposedHeaders(List.of("Location", "X-Request-Id"));
		cors.setMaxAge(Duration.ofHours(1));
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", cors);
		return source;
	}

}
