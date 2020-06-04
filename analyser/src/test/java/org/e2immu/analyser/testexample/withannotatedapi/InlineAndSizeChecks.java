package org.e2immu.analyser.testexample.withannotatedapi;

import org.e2immu.annotation.Constant;
import org.e2immu.annotation.NotNull;

import java.util.Objects;

/*

this test is a combination of @Size info on String,
with an inline replacement of a conditional,
and the connection between a conditional and the NOT_NULL property

 */
public class InlineAndSizeChecks {

    private static int len(String s) {
        return s == null ? -1 : s.length();
    }

    @Constant(intValue = 3)
    public static final int m1 = len("abc");

    @Constant(intValue = -1)
    public static final int m2 = len(null);

    public static void method1(@NotNull String in1) {
        Objects.requireNonNull(in1);
        int l1 = len(in1);
        // if all is well, l should have the value "in1.length()", which has a @Size annotation, therefore always >=0
        if (l1 >= 0) { // should raise an error
            System.out.println("Always true");
        }
    }

    // simply a different way of causing the @NotNull
    // The added complication is that we rely on the @Size(copy = true) of toLowerCase()
    // (meaning that in2.toLowerCase().length() == in2.length())
    public static void method2(@NotNull String in2) {
        int l2 = len(in2.toLowerCase());
        // if all is well, l should have the value "in1.length()", which has a @Size annotation, therefore always >=0
        if (l2 >= 0) { // should raise an error
            System.out.println("Always true");
        }
    }
}
