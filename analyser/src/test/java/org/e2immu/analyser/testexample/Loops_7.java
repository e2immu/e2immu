package org.e2immu.analyser.testexample;

public class Loops_7 {

    public static void method(int n) {
        int i = 0;
        while (i < n) {
            int k = i;
            i++;
            if (k + 1 == i) {
                break;
            }
            System.out.println("unreachable statement");
        }
    }
}
