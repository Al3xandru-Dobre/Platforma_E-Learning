package service;

import interfaces.User;

import java.io.IOException;
import java.util.Date;


public class Note {
    private String title;
    private String content;
    private Date dataOfCreation;
    private User author;

    public Note(User us) throws IOException {
        this.title = Utilities.readString("Introduce-ti titlul acestei notite");
        this.content = Utilities.readString("Introduce-ti continutul acestui memo");
        this.dataOfCreation = new Date();
        this.author = us;
    }

    //anonymous Note
    public Note() throws IOException {
        this.title = Utilities.readString("Introduce-ti titlul acestei notite");
        this.content = Utilities.readString("Introduce-ti continutul acestui memo");
        this.dataOfCreation = new Date();
        this.author = null;
    }
}
