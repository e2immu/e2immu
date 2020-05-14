package org.e2immu.analyser.testexample;

import org.e2immu.annotation.AnnotationType;
import org.e2immu.annotation.Constant;
import org.e2immu.annotation.NotNull;

public class LoopStatementChecks {

    @Constant(stringValue = "abc")
    @NotNull
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
    @NotNull(type = AnnotationType.VERIFY_ABSENT)
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

    // important here is that i==0 is not a constant expression, because i is a loop variable
    // the interesting value to check here is 1, because the i++ is evaluated BEFORE the i<10 and the i++
    // at the moment
    public static int method4() {
        for(int i=0; i<10; i++) {
            if(i == 1) return 4;
        }
        return 0;
    }

    // same as in 4
    public static int method5() {
        int i=0;
        for(; i<10; i++) {
            if(i == 1) return 5;
        }
        return 0;
    }

    public static void method6() {
        int i=0;
        for(; i<10; i++) {
            int j=3; // ERROR: variable is not used
        }
    }
}
