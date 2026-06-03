package interfaces;

import exception.InvalidName;
import org.mindrot.jbcrypt.BCrypt;
import service.Utilities;

public abstract class User {
    private String name;
    private final String email;
    String hashPass;  // package-accessible for DB reconstruction

    private static final int BYCRYPT_COST = 12;

    public User(String nume, String parola, String mail) {
        setName(nume);
        setPassword(parola);
        this.email = mail;
    }

    /**
     * Called ONLY by UserRepository.Reconstructed* subclasses to inject
     * the BCrypt hash stored in the database, bypassing re-hashing.
     * This avoids a second 300ms BCrypt call just to reconstruct a User.
     */
    public void overrideHash(String storedHash) {
        this.hashPass = storedHash;
    }

    private static void validatePasswrod(String p) {
        if (p == null || p.isBlank()) {
            throw new IllegalArgumentException("Nu ai introdus nimic");
        }
        if (p.length() < 10) {
            throw new IllegalArgumentException("Parola trebuie sa aiba minim 10 caractere");
        }
        if (!Utilities.containsAtLeast(p, '!', 1) && !Utilities.containsAtLeast(p, '@', 1)) {
            throw new IllegalArgumentException("Parola trebuie sa contina ! sau @");
        }
    }

    private void setName(String n) {
        if (n == null || n.isBlank()) throw new InvalidName(InvalidName.Reason.EMPTY, n);
        if (n.length() < 3)          throw new InvalidName(InvalidName.Reason.TOO_SHORT, n);
        if (!n.matches("[\\p{L}\\s'\\-]+"))
            throw new InvalidName(InvalidName.Reason.INVALID_CHARACTER, n);
        if (n.length() > 30)         throw new InvalidName(InvalidName.Reason.TOO_LONG, n);
        this.name = n.strip();
    }

    private void setPassword(String newPassword) {
        validatePasswrod(newPassword);
        this.hashPass = BCrypt.hashpw(newPassword, BCrypt.gensalt(BYCRYPT_COST));
    }

    public boolean checkPasssword(String input) {
        return BCrypt.checkpw(input, this.hashPass);
    }

    public String getName()     { return name; }
    public String getEmail()    { return email; }
    /** Returns the BCrypt hash stored in this object. Used ONLY by UserRepository.save(). */
    public String getHashPass() { return hashPass; }
    public abstract String getRole();
}