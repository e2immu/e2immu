package org.e2immu.analyser.testexample;

import org.e2immu.annotation.NotNull;

public class Loops_3 {

    @NotNull(absent = true)
    public static String method() {
        String res = null; // = null forced upon us by compiler
        for (String s : new String[]{}) {
            res = s;
        }
        // we should have kept the assignment, knowing it is not null
        return res;
    }

}
