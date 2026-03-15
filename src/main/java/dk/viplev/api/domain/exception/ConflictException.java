package dk.viplev.api.domain.exception;

public class ConflictException extends RuntimeException {

	private final String description;
	private final String message;

	public ConflictException(String message) {
		this(message, "");
	}

	public ConflictException(String message, String description) {
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
