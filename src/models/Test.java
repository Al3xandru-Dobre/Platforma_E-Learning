package models;

import exception.InsufficientGrade;

public class Test {
    private static final double MINIMUM_PASSING_GRADE = 5.0;
    private static final double MAXIMUM_GRADE = 10.0;

    private String content;
    private double grade;

    public Test(String content){
        this.content = content;
        this.grade = -1;
    }

    public void setNota(double nota) {
        if (nota < 0 || nota > MAXIMUM_GRADE) {
            throw new IllegalArgumentException(
                    "Nota trebuie sa fie intre 0 si " + MINIMUM_PASSING_GRADE
            );
        }
        this.grade = nota;

        if (nota < MINIMUM_PASSING_GRADE) {
            throw new InsufficientGrade(nota);
        }
    }

    public boolean isPassed(){
        return grade >= MINIMUM_PASSING_GRADE;
    }
}
