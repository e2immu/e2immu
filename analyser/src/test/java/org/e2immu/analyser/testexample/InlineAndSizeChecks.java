package org.e2immu.analyser.testexample;

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

    private static int len2(String s) {
        System.out.println("Computing the length of " + s);
        return s == null ? -1 : s.length();
    }

    private static int len3(String s) {
        System.out.println("Computing the length of " + s);
        int t = s == null ? -1 : s.length();
        System.out.println("Result is " + t);
        return t;
    }

    // TODO near future work
    private static int len4(String s) {
        int t = -1;
        if (s != null) t = s.length();
        return t;
    }

    // TODO near future work
    private static int len5(String s) {
        int t;
        if (s == null) t = -1;
        else {
            System.out.println("Computing the length of " + s);
            t = s.length();
        }
        return t;
    }

    // ok check that this can be inlined
    private static int len6(String s) {
        if (s == null) return -1;
        return s.length();
    }

    private static int len7(String s) {
        if (s == null) return -1;
        int t = s.length();
        System.out.println("Length is " + t);
        return t;
    }

    @Constant(intValue = 3)
    public static final int m0 = "abc".length();

    @Constant(intValue = 3)
    public static final int m1 = len("abc");

    @Constant(intValue = -1)
    public static final int m2 = len(null);

    @Constant(intValue = -1)
    public static final int m2_2 = len2(null);

    @Constant(intValue = -1)
    public static final int m2_3 = len3(null);


    public static void method1(@NotNull String in1) {
        Objects.requireNonNull(in1);
        int l1 = len(in1);
        // if all is well, l should have the value "in1.length()", which has a @Size annotation, therefore always >=0
        if (l1 >= 0) { // should raise an error
            System.out.println("Always true");
        }
    }

    // simply a different way of causing the @NotNull
    public static void method2(@NotNull String in2) {
        int l2 = len(in2.toLowerCase());
        // TODO for now, in2.toLowerCase().length() is not reduced to in2.length()
        // if all is well, l should have the value "in1.length()", which has a @Size annotation, therefore always >=0
        if (l2 >= 0) { // should raise an error
            System.out.println("Always true");
        }
    }
}
