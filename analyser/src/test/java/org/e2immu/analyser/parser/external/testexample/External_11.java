package org.e2immu.analyser.parser.external.testexample;

public class External_11 {
    public static boolean method(String s) {
        char array[] = s.toCharArray();
        for (int i = 0; i < array.length; i++) {
            boolean d = array[i] >= '0' && array[i] <= '9';
            boolean u = array[i] >= 'A' && array[i] <= 'Z';
            if (!((d) || (u && array[i] != 'Z' && array[i] != 'X'))) {
                return true;
            }
        }
        return false;
    }
}
