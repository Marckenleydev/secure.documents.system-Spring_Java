package marc.dev.secure_document_system.exception;



public class ApiException extends RuntimeException {
    public ApiException(String message) { super(message); }
    public ApiException() { super("An error occurred"); }
}
