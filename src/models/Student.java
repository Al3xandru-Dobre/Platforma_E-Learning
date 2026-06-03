package models;

import interfaces.User;
import java.util.Set;
import java.util.LinkedHashSet;

public class Student extends User {

    private Set<String> attendedCourses = new LinkedHashSet<>();
    private double progress;
    public Student (String name, String pas, String email){
        super(name,pas,email);
    }

    @Override
    public String getRole() {return "Student";}


    public void attendCourse(String courseName){
        attendedCourses.add(courseName);
    }

    public void dropCourse(String courseName) {
        attendedCourses.remove(courseName);
    }
}
