package dk.viplev.api.adapter.inbound.rest.error;

import dk.viplev.api.adapter.inbound.rest.dto.ErrorDTO;
import dk.viplev.api.domain.exception.BadRequestException;
import dk.viplev.api.domain.exception.ConflictException;
import dk.viplev.api.domain.exception.ForbiddenException;
import dk.viplev.api.domain.exception.NotFoundException;
import dk.viplev.api.domain.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.AuthenticationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@Slf4j
@RestControllerAdvice
public class RestErrorHandler {

	private static final URI BAD_REQUEST_TYPE_URI = URI.create("https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/400");
	private static final URI UNAUTHORIZED_TYPE_URI = URI.create("https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/401");
	private static final URI FORBIDDEN_TYPE_URI = URI.create("https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/403");
	private static final URI NOT_FOUND_TYPE_URI = URI.create("https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/404");
	private static final URI CONFLICT_TYPE_URI = URI.create("https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/409");
	private static final URI INTERNAL_SERVER_ERROR_TYPE_URI = URI.create("https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/500");

	@ExceptionHandler(BadRequestException.class)
	public ResponseEntity<ErrorDTO> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
		log.info("HTTP ERROR - 400 Bad Request: {}", ex.getDescription());

		ErrorDTO error = new ErrorDTO()
			.status(400)
			.type(BAD_REQUEST_TYPE_URI)
			.title(ex.getMessage())
			.detail(ex.getDescription())
			.instance(URI.create(request.getRequestURI()));

		return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorDTO> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpServletRequest request) {
		log.info("HTTP ERROR - 400 Bad Request: {}", ex.getMessage());

		ErrorDTO error = new ErrorDTO()
			.status(400)
			.type(BAD_REQUEST_TYPE_URI)
			.title("Bad Request")
			.detail("Request body is invalid or missing")
			.instance(URI.create(request.getRequestURI()));

		return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(UnauthorizedException.class)
	public ResponseEntity<ErrorDTO> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
		log.info("HTTP ERROR - 401 Unauthorized: {}", ex.getDescription());

		ErrorDTO error = new ErrorDTO()
			.status(401)
			.type(UNAUTHORIZED_TYPE_URI)
			.title("Unauthorized - " + ex.getMessage())
			.detail(ex.getDescription())
			.instance(URI.create(request.getRequestURI()));

		return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
	}

	@ExceptionHandler(ForbiddenException.class)
	public ResponseEntity<ErrorDTO> handleForbidden(ForbiddenException ex, HttpServletRequest request) {
		log.info("HTTP ERROR - 403 Forbidden: {}", ex.getMessage());

		ErrorDTO error = new ErrorDTO();
		error.setDetail(ex.getDescription());
		error.setInstance(URI.create(request.getRequestURI()));
		error.setStatus(403);
		error.setTitle("Forbidden - " + ex.getMessage());
		error.setType(FORBIDDEN_TYPE_URI);

		return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
	}

	@ExceptionHandler(NotFoundException.class)
	public ResponseEntity<ErrorDTO> handleNotFound(NotFoundException ex, HttpServletRequest request) {
		log.info("HTTP ERROR - 404 Not Found: {}", ex.getDescription());

		ErrorDTO error = new ErrorDTO();
		error.setDetail(ex.getDescription());
		error.setInstance(URI.create(request.getRequestURI()));
		error.setStatus(404);
		error.setTitle("Not Found - " + ex.getMessage());
		error.setType(NOT_FOUND_TYPE_URI);

		return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
	}

	@ExceptionHandler(ConflictException.class)
	public ResponseEntity<ErrorDTO> handleConflict(ConflictException ex, HttpServletRequest request) {
		log.info("HTTP ERROR - 409 Conflict: {}", ex.getDescription());

		ErrorDTO error = new ErrorDTO();
		error.setDetail(ex.getDescription());
		error.setInstance(URI.create(request.getRequestURI()));
		error.setStatus(409);
		error.setTitle("Conflict - " + ex.getMessage());
		error.setType(CONFLICT_TYPE_URI);

		return new ResponseEntity<>(error, HttpStatus.CONFLICT);
	}

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ErrorDTO> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
		log.info("HTTP ERROR - 401 Unauthorized: {}", ex.getMessage());

		ErrorDTO error = new ErrorDTO()
			.status(401)
			.type(UNAUTHORIZED_TYPE_URI)
			.title("Unauthorized")
			.detail("Invalid credentials")
			.instance(URI.create(request.getRequestURI()));

		return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
	}

	// internal server error handler
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorDTO> handleInternalServerError(Exception ex, HttpServletRequest request)
	{
		log.error("HTTP ERROR - 500 Internal Server Error: {}", ex.getMessage(), ex);

		ErrorDTO error = new ErrorDTO();
		error.setDetail("An unexpected error occurred.");
		error.setInstance(URI.create(request.getRequestURI()));
		error.setStatus(500);
		error.setTitle("Internal Server Error");
		error.setType(INTERNAL_SERVER_ERROR_TYPE_URI);

		return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
	}






}
