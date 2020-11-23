package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Constant;

public class DependentVariables {

    @Constant("56")
    static int method1(int a) {
        int[] array = new int[3];
        array[0] = 12;
        array[1] = 13;
        array[2] = 31;
        return array[0] + array[1] + array[2];
    }

    @Constant("12")
    static int method2(int a) {
        int[] array = new int[3];
        int b = a;
        array[b] = 12;
        return array[a];
    }
}
