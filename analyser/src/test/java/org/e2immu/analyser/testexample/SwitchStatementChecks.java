package org.e2immu.analyser.testexample;

import org.e2immu.annotation.AnnotationType;
import org.e2immu.annotation.Constant;
import org.e2immu.annotation.NotNull;

public class SwitchStatementChecks {

    @NotNull
    @Constant(type = AnnotationType.VERIFY_ABSENT)
    public static String method1(char c) {
        switch (c) {
            case 'a':
                return "a";
            case 'b':
                return "b";
            default:
                return "c";
        }
    }

    @NotNull(type = AnnotationType.VERIFY_ABSENT)
    @Constant(type = AnnotationType.VERIFY_ABSENT)
    public static String method1(char c, String b) {
        switch (c) {
            case 'a':
                return "a";
            case 'b':
                return "b";
            default:
                return b;
        }
    }

    @NotNull
    @Constant(type = AnnotationType.VERIFY_ABSENT)
    public static String method3(char c, String b) {
        switch (c) {
            case 'a':
                return "a";
            case 'b':
                return "b";
            default:
                // ERROR 1: this should raise an error (if statement expression always evaluates to false)
                if (c == 'a' || c == 'b') return b;
                return "c";
        }
    }

    @NotNull(type = AnnotationType.VERIFY_ABSENT)
    @Constant(type = AnnotationType.VERIFY_ABSENT)
    public static String method4(char c, String b) {
        switch (c) {
            default:
        }
        return b;
    }

    @NotNull
    @Constant(type = AnnotationType.VERIFY_ABSENT)
    public static String method5(char c) {
        String res;
        switch (c) {
            case 'a':
                res = "a";
                break;
            case 'b':
                res = "b";
                break;
            default:
                res = "c";
        }
        return res;
    }

    // TODO this one works like method5 at the moment, we don't have any support for
    // not having break statements... or should we block this?
    @NotNull
    @Constant(type = AnnotationType.VERIFY_ABSENT)
    public static String method6(char c) {
        String res;
        switch (c) {
            case 'a':
                res = "a";
            case 'b':
                res = "b";
            default:
                res = "c";
        }
        return res;
    }


    @NotNull
    @Constant(stringValue = "a")
    public static String method7(char c) {
        String res;
        char d = 'a';
        switch (d) { // ERROR 3 & 4: evaluates to constant
            case 'a':
                res = "a";
                break;
            case 'b':
                res = "b";
                break;
            default:
                res = "c";
        }
        return res;
    }


}

