package dk.viplev.api.domain.exception;

public class NotFoundException extends RuntimeException {

	private final String description;
	private final String message;

	public NotFoundException(String message) {
		this(message, "");
	}

	public NotFoundException(String message, String description) {
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