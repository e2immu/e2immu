package org.e2immu.analyser.testexample;


import java.io.IOException;
import java.io.Writer;
import java.util.List;

public class VariableScope_4 {
    // only difference between VS_4 and VS_2 is the redundant = null on ioe (statement 0)

    static IOException writeLine(List<String> list, Writer writer) {
        IOException ioe = null;
        try {
            for (String outputElement : list) {
                writer.write(outputElement);
            }
            return null;
        } catch (IOException e) {
            ioe = e;
        }
        return ioe; // should not contain a reference to 'e'
    }
}
