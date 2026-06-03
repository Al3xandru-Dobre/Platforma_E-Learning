package models;
import java.util.Optional;
import java.util.OptionalInt;

public class Feedback {
    private String message;
    private Optional<Exercise> exercise;
    private Optional<Test> test;

    private Feedback(String message, Exercise exercise, Test test) {
        this.message = message;
        this.exercise = Optional.ofNullable(exercise);
        this.test = Optional.ofNullable(test);
    }
    public static Feedback pentruExercitiu(String mesaj, Exercise e) {
        if (e == null) throw new IllegalArgumentException("Exercitiul nu poate fi null");
        return new Feedback(mesaj, e, null);
    }
    public static Feedback pentruTest(String mesaj, Test t) {
        if (t == null) throw new IllegalArgumentException("Testul nu poate fi null");
        return new Feedback(mesaj, null, t);
    }
}
