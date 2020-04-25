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
                // this should raise an error (if statement expression always evaluates to false)
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
}

