package org.e2immu.analyser.testexample;

import org.e2immu.annotation.NotNull;

public class Loops_2 {

    @NotNull
    public static String method() {
        String res = null; // = null forced upon us by compiler
        for (String s : new String[]{"a", "b", "c"}) {
            res = s;
        }
        // we should have kept the assignment, knowing it is not null
        return res;
    }

}
