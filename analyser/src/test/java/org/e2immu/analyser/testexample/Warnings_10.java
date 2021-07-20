package org.e2immu.analyser.testexample;

public class Warnings_10 {
    public static int method(int i) {
        int j = i;
        j = j; // useless self-assignment
        return j;
    }
}
