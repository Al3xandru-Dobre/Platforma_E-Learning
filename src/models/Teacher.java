package models;
import java.util.List;
import  java.util.ArrayList;

import interfaces.User;

public class Teacher extends User {
    private enum Subject{
        MATH,
        COMPUTER_SCIENCE,
        AI,
        MACHINE_LEARNING,
        PHYSICS,
        LITERATURE,
        PHILOSOPHY,
        VIDEO_EDITING
    }

    private List<Course> createdCourses = new ArrayList<Course>();
    private Subject specializare;
    public Teacher(String name, String pass, String email,Subject specializare){
        super(name,pass,email);
        this.specializare = specializare;
    }
    @Override
    public String getRole() {return "Teacher";}

    public void createCourse(Course nameCourse){
        createdCourses.add(nameCourse);
    }
}
