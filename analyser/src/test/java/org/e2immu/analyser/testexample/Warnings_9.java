package org.e2immu.analyser.testexample;

public class Warnings_9 {
    public static int method(int i) {
        int j = i;
        j = 3; // useless assignment in 1st statement
        return j;
    }
}
