package org.e2immu.analyser.testexample;

public class Loops_14 {

    public static int method(int n) {
        int i = 0;
        int j = 9;
        while (i < n) {
            if (i == 3) {
                j = 10;
            }
            if (i == 6) {
                j = 11;
            }
            i++;
        }
        return j;
    }

}
