package org.e2immu.analyser.testexample;


import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.Nullable;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

public class VariableScope_2 {
    // only difference between VS_4 and VS_2 is the redundant = null on ioe (statement 0)
    // similar test in TryStatement_6

    @Nullable
    static IOException writeLine(@NotNull List<String> list, @NotNull Writer writer) {
        IOException ioe;
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
