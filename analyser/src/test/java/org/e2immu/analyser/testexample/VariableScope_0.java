package org.e2immu.analyser.testexample;


public class VariableScope_0 {

    static int method(int i) {
        {
            int j = i;
            if (j > 3) {
                return j;
            }
        }
        System.out.println("i is " + i); // here, j does not exist!
        int j = 3 * i;
        return j;
    }
}
