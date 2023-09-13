package org.e2immu.analyser.parser.loops.testexample;

import java.util.List;

/*
Test causes crash when the increment pos++ is absent.
Underlying problem: the variable 'pos' exists in 3.0.1 but has no value.
 */
public class Loops_26 {

    public static String method(List<String> in) {
        int end = in.size();
        int pos = 0;
        String s;
        while (pos < end && (s = in.get(pos)).endsWith(".")) {
            System.out.println(end);
            if (s.startsWith("x")) {
                return "y";
            }
            //pos++;
        }
        return "n";
    }
}
