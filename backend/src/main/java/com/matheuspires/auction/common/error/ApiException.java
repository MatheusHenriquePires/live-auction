package com.matheuspires.auction.common.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Expected business error, rendered as an RFC 7807 problem with a domain {@link ErrorCode}.
 * <p>
 * No stack trace is captured: these are normal outcomes (a rejected bid is not a bug), and some of
 * them happen on hot paths.
 */
public class ApiException extends RuntimeException {

	private final ErrorCode code;

	private final Map<String, Object> properties = new LinkedHashMap<>();

	public ApiException(ErrorCode code, String detail) {
		super(detail, null, false, false);
		this.code = code;
	}

	public static ApiException notFound(String resource) {
		return new ApiException(ErrorCode.NOT_FOUND, resource + " not found");
	}

	/** Adds an extra field to the problem body, e.g. {@code minimumAmount}. */
	public ApiException with(String name, Object value) {
		properties.put(name, value);
		return this;
	}

	public ErrorCode code() {
		return code;
	}

	public Map<String, Object> properties() {
		return Collections.unmodifiableMap(properties);
	}

}
