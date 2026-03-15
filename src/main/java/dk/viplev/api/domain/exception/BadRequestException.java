package dk.viplev.api.domain.exception;

public class BadRequestException extends RuntimeException {

	private final String description;
	private final String message;

	public BadRequestException(String message) {
		this(message, "");
	}

	public BadRequestException(String message, String description) {
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
