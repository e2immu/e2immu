package org.e2immu.analyser.testexample;

public class EvaluationErrors {

    public static int testDivisionByZero() {
        int i=0;
        // ERROR 1: division by zero
        int j = 23 / i;
        return j;
    }

    public static int testDeadCode() {
        int i=1;
        // ERROR 2: evaluation in if-statement is constant
        if(i != 1) {
            System.out.println("hello");
        }
        return 3;
    }
}
