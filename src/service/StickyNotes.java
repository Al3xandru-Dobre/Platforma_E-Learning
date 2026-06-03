package service;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import interfaces.Instrument;
import interfaces.User;

import java.util.Optional;
public class StickyNotes extends Instrument {
    private List<Note> notes = new ArrayList<>();
    private Optional<User> calledBy = Optional.empty();

    public StickyNotes(String name) {
        super(name);
    }

    public StickyNotes(String name, User creator) {
        super(name);
        calledBy = Optional.ofNullable(creator);
    }
    @Override
    public void functionality() throws IOException {
        // functionalities: create a sticky note and write in it
        if(calledBy.isPresent()) {
            Note note = new Note(calledBy.get());
        } else {
            Note note = new Note();
        }
    }
}
