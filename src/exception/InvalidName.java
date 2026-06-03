package exception;

public class InvalidName extends RuntimeException {
    public enum Reason {
        EMPTY,
        TOO_SHORT,
        TOO_LONG,
        INVALID_CHARACTER
    }

    public final Reason motiv;
    private final String receivedValue;

    public InvalidName(Reason motiv, String receivedValue) {
        super(buildMessage(motiv,receivedValue));
        this.motiv = motiv;
        this.receivedValue = receivedValue;
    }

    private static String buildMessage(Reason motiv, String val) {
        return switch (motiv){
            case EMPTY -> "Numele nu poate fi gol sau doar spatii libere";
            case TOO_LONG -> "Numele are mai mult de 30 de caractere";
            case TOO_SHORT -> "Numele" + val + "e prea scurt (minim 10 caractere)";
            case INVALID_CHARACTER -> "Numele" + val + "contine caractere interzise !";
        };
    }
}
