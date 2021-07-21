package org.e2immu.analyser.testexample;



import java.io.IOException;
import java.util.List;
import java.util.function.Predicate;

// as part of Formatter and the testing of Test_Output_03_Formatter
// in this example, 'e' being used 2x is not an issue; in FormatterSimplified_6, it is/was
public class TryStatement_5 {

    interface Writer {
        void write(int i) throws IOException;
    }

    static void writeLine(List<String> list, Writer writer, int start, int end) throws IOException {
        try {
            forward(list, forwardInfo -> {
                try {
                    if (forwardInfo == null) {
                        writer.write(start);
                        return start == end;
                    }
                    return forwardInfo.length() == end;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            throw new IOException(e);
        }
    }

    static void forward(List<String> list, Predicate<String> predicate) {

    }
}
