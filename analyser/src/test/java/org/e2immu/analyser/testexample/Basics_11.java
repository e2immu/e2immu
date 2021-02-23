package org.e2immu.analyser.testexample;

import org.e2immu.annotation.NotNull;

/*
tests static assignments and context not null

No annotated APIs have been loaded. Println will be modifying out.
toLowerCase cannot modify s2, because String is hard-wired to be @E2Container
 */
public class Basics_11 {

    public static void test(@NotNull String in) {
        String s1 = in;
        String s2 = in;
        System.out.println(s1); // does not cause context not null
        System.out.println(s2.toLowerCase()); // does cause context not null
        assert s1 != null; // should always be true
    }

}
