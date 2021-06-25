package org.e2immu.analyser.testexample;

import java.util.function.Function;

/* inspection problem, return type of writer.apply */

public class FormatterSimplified_1 {

    record ForwardInfo(int pos, int chars, String string, boolean symbol) {
    }

    static void forward(Function<ForwardInfo, Boolean> writer, int start) {
        if (writer.apply(new ForwardInfo(start, 9, null, false))) return;
    }
}
