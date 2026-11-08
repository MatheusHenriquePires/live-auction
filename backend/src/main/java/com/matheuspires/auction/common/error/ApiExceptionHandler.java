package com.matheuspires.auction.common.error;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.matheuspires.auction.common.web.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error into {@code application/problem+json} with {@code code} and {@code traceId}.
 * Security failures are routed here too (see {@code SecurityProblemHandler}), so there is one format.
 * Stack traces and internal messages never reach the client.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
		ProblemDetail problem = problem(ex.code().status(), ex.code().name(), ex.getMessage());
		ex.properties().forEach(problem::setProperty);
		return ResponseEntity.status(ex.code().status()).body(problem);
	}

	@ExceptionHandler(AuthenticationException.class)
	ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
			.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
			.body(problem(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.name(), "Missing or invalid access token"));
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
			.body(problem(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN.name(), "You are not allowed to perform this action"));
	}

	@ExceptionHandler(OptimisticLockingFailureException.class)
	ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(problem(HttpStatus.CONFLICT, ErrorCode.CONFLICT.name(),
					"The resource was modified concurrently, please retry"));
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex) {
		log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(problem(HttpStatus.CONFLICT, ErrorCode.CONFLICT.name(), "The request conflicts with existing data"));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
		log.error("Unhandled exception", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(problem(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR.name(),
					"Unexpected error, please report the traceId"));
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail problem = ex.getBody();
		problem.setDetail("Request validation failed");
		problem.setProperty("errors", fieldErrors(ex.getBindingResult()));
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
		if (response != null && response.getBody() instanceof ProblemDetail problem) {
			enrich(problem, ErrorCode.forStatus(statusCode));
		}
		return response;
	}

	private static ProblemDetail problem(HttpStatusCode status, String code, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		enrich(problem, code);
		return problem;
	}

	private static void enrich(ProblemDetail problem, String code) {
		Map<String, Object> properties = problem.getProperties();
		if (properties == null || !properties.containsKey("code")) {
			problem.setProperty("code", code);
		}
		problem.setProperty("traceId", CorrelationIdFilter.currentTraceId());
	}

	private static List<Map<String, String>> fieldErrors(BindingResult result) {
		List<Map<String, String>> errors = new ArrayList<>();
		result.getFieldErrors()
			.forEach(error -> errors.add(Map.of("field", error.getField(), "message",
					Objects.requireNonNullElse(error.getDefaultMessage(), "is invalid"))));
		result.getGlobalErrors()
			.forEach(error -> errors.add(Map.of("field", error.getObjectName(), "message",
					Objects.requireNonNullElse(error.getDefaultMessage(), "is invalid"))));
		return errors;
	}

}
