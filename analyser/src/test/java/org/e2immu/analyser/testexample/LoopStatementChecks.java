package org.e2immu.analyser.testexample;

import org.e2immu.annotation.AnnotationType;
import org.e2immu.annotation.Constant;
import org.e2immu.annotation.NotNull;

public class LoopStatementChecks {

    @Constant(stringValue = "abc")
    public static String method1(int n) {
        String res1;
        int i=0;
        while(true) { // executed at least once, which means that res1 MUST be equal to "abc"
            res1 = "abc";
            i++;
            if(i>=n) break;
        }
        return res1;
    }

    @Constant(type = AnnotationType.VERIFY_ABSENT)
    public static String method2(int n) {
        String res2 = null; // = null forced upon us by compiler!
        int i=0;
        while(true) { // executed at least once, but assignment may not be reachable
            i++;
            if(i>=n) break;
            res2 = "abc";
        }
        return res2;
    }

    @NotNull
    public static String method3() {
        String res = null; // = null forced upon us by compiler
        for(String s: new String[] { "a", "b", "c"}) {
            res = s;
        }
        // we should have kept the assignment, knowing it is not null
        return res;
    }
}
