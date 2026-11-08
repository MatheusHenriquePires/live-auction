package com.matheuspires.auction.common.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/** Machine-readable error codes returned in the {@code code} field of every problem response. */
public enum ErrorCode {

	VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
	FORBIDDEN(HttpStatus.FORBIDDEN),
	NOT_FOUND(HttpStatus.NOT_FOUND),
	CONFLICT(HttpStatus.CONFLICT),
	EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT),
	AUCTION_NOT_CANCELLABLE(HttpStatus.CONFLICT),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

	private final HttpStatus status;

	ErrorCode(HttpStatus status) {
		this.status = status;
	}

	public HttpStatus status() {
		return status;
	}

	/** Code for framework-raised errors that only carry an HTTP status (malformed JSON, 404, 405...). */
	public static String forStatus(HttpStatusCode status) {
		if (status.value() == HttpStatus.BAD_REQUEST.value()) {
			return VALIDATION_ERROR.name();
		}
		if (status.is5xxServerError()) {
			return INTERNAL_ERROR.name();
		}
		HttpStatus resolved = HttpStatus.resolve(status.value());
		return resolved != null ? resolved.name() : "HTTP_" + status.value();
	}

}
