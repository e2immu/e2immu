package org.e2immu.analyser.testexample;

import org.e2immu.annotation.NotNull;

/*
tests static assignments and context not null
 */
public class Basics_11 {

    public static void method1(@NotNull String in) {
        String s1 = in;
        String s2 = in;
        System.out.println(s1); // does not cause context not null
        System.out.println(s2.toLowerCase()); // does cause context not null
        assert s1 != null; // should always be true
    }

}
