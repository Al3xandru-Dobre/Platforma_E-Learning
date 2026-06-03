package models;

import interfaces.User;

public class Administrator extends User {
    public Administrator(String name, String pass, String email) {
        super(name,pass,email);
    }


    @Override
    public String getRole(){return "Administrator";}

    public void deleteUser(User u) {
        //placeholder
    }
}
