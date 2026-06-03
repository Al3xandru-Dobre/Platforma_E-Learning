package models;

public class Exercise {
    private String problems_text;

    public Exercise(String problems) {
        this.problems_text = problems;
    }

    public String getExercise() {return problems_text; }
}
