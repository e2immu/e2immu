package org.e2immu.analyser.testexample;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

public class Warnings_12 {

    // warning for throws
    static IOException writeLine(List<String> list, Writer writer) throws IOException {
        IOException ioe = null; // redundant assignment
        try {
            for (String string : list) {
                writer.write(string);
            }
            return null;
        } catch (IOException e) {
            ioe = e;
        }
        return ioe;
    }
}
