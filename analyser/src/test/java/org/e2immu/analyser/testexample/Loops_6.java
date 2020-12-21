package org.e2immu.analyser.testexample;

public class Loops_6 {

    public static void method() {
        int i = 0;
        for (; i < 10; i++) {
            int j = 3; // ERROR: variable is not used
        }
    }
}
