package dk.viplev.api.domain.exception;

public class ForbiddenException extends RuntimeException {

	private final String description;
	private final String message;

	public ForbiddenException(String message) {
		this(message, "");
	}

	public ForbiddenException(String message, String description) {
		super(message);
		this.message = message;
		this.description = description;
	}

	public String getDescription() {
		if (description == null) {
			return this.message;
		}
		return this.description;
	}
}
