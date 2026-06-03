package interfaces;

import java.io.IOException;

public abstract class Instrument {
    public String name;

    public Instrument(String name) {
        this.name = name;
    }

    public abstract void functionality() throws IOException;
}
