package org.e2immu.analyser.testexample;


import java.util.Random;

public class VariableScope_1 {

    /*
    convoluted code to ensure that k cannot simply be expressed in terms of a parameter
    we want j to have a "value of its own"
     */
    static int method() {
        int k;
        {
            int j = 0;
            Random r = new Random();
            for (int i = 0; i < 10; i++) {
                j += r.nextInt();
            }
            k = j;
        }
        return k; // should not refer to j!
    }
}
