package ui.util;

import interfaces.User;
import models.Administrator;
import models.Student;
import models.Teacher;

import java.util.Optional;

public class UserSession {

    private static final UserSession INSTANCE = new UserSession();
    private User currentUser = null;
    private UserSession() {}

    public static UserSession get() {return INSTANCE;}

    public void login(User user){
        if(user == null ) throw new IllegalArgumentException("User null cannot be logged");
        this.currentUser = user;
    }

    public void logout(){
        this.currentUser = null;
    }

    public boolean isLoggedIn() {return currentUser != null; }
    public Optional<User> currentUser() {return Optional.ofNullable(currentUser); }

    public Student asStudent() { return (Student) currentUser;}
    public Teacher asTeacher() { return (Teacher) currentUser;}
    public Administrator asAdmin() { return (Administrator) currentUser;}

    public String role(){
        return currentUser != null ? currentUser.getRole() : "Guest";
    }
}
