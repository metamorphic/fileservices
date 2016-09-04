package io.metamorphic.fileservices;

/**
 * Created by markmo on 11/07/2015.
 */
public class LinesContainer {

    public String[] lines;
    public String lineEnding;

    public LinesContainer(String[] lines, String lineEnding) {
        this.lines = lines;
        this.lineEnding = lineEnding;
    }
}
