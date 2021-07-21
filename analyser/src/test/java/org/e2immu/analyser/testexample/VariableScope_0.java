package org.e2immu.analyser.testexample;


import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.function.Predicate;

public class VariableScope_0 {
    record OutputElement(String guide, String string, int pos) {
    }

    // there's a problem with 'e' being used 2x
    void writeLine(List<OutputElement> list, Writer writer, int start, int end) throws IOException {
        try {
            forward(list, forwardInfo -> {
                try {
                    if (forwardInfo.guide == null) {
                        writer.write(forwardInfo.string);
                    }
                    return forwardInfo.pos == end;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, start);
        } catch (RuntimeException e) {
            throw new IOException(e);
        }
    }

    static void forward(List<OutputElement> list, Predicate<OutputElement> consumer, int pos) {

    }
}
