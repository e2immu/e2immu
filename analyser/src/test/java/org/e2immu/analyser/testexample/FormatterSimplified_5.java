package org.e2immu.analyser.testexample;


import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.function.Predicate;

// as part of Formatter and the testing of Test_Output_03_Formatter

public class FormatterSimplified_5 {

    static void writeLine(List<String> list, Writer writer, int start, int end) throws IOException {
        try {
            forward(list, forwardInfo -> {
                try {
                    if (forwardInfo == null) {
                        writer.write(start);
                        return start == end;
                    }
                    return forwardInfo.length() == end;
                } catch (IOException f) {
                    throw new RuntimeException(f);
                }
            });
        } catch (RuntimeException e) {
            throw new IOException(e);
        }
    }

    static void forward(List<String> listParam, Predicate<String> predicateParam) {

    }
}
