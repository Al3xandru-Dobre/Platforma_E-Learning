package exception;

public class InvalidString extends RuntimeException {
    public InvalidString(String message) {
        super(message);
    }
}
