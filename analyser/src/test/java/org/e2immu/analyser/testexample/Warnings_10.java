package org.e2immu.analyser.testexample;

public class Warnings_10 {
    public static int method(int i) {
        int j = i;
        j = j; // useless self-assignment
        return j;
    }

    public static int method2(int i) {
        int j = 0; // no error here
        if(i == 3) {
            j = i;
        }
        return j;
    }

    public static int method3(int i) {
        int j = 0; // error here
        if(i == 3) {
            j = i;
        } else {
            j = -i;
            System.out.println(j);
        }
        return j;
    }

    public static int method4(int i) {
        int j = 0; // no error here
        if(i == 3) {
            System.out.println(j);
            j = i;
        } else {
            j = -i;
        }
        return j;
    }

    public static int method5(int i) {
        int j = 0; // no error here neither, but more tricky
        if(i == 3) {
            System.out.println(j);
            j = i;
            System.out.println(j);
        } else {
            j = -i;
        }
        return j;
    }
}
