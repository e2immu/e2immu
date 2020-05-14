package org.e2immu.analyser.testexample;

import org.e2immu.annotation.AnnotationType;
import org.e2immu.annotation.Constant;
import org.e2immu.annotation.NotNull;

public class TryStatementChecks {

    @NotNull
    @Constant(type = AnnotationType.VERIFY_ABSENT)
    public static String method1(String s) {
        try {
            return "Hi" + Integer.parseInt(s);
        } catch (NullPointerException npe) {
            return "Null";
        } catch (NumberFormatException nfe) {
            return "Not a number";
        }
    }

    @NotNull
    @Constant(type = AnnotationType.VERIFY_ABSENT)
    public static String method2(String s) {
        String res;
        try {
            res = "Hi" + Integer.parseInt(s);
        } catch (NullPointerException npe) {
            res = "Null";
        } catch (NumberFormatException nfe) {
            res = "Not a number";
        }
        return res;
    }

    @NotNull
    @Constant(stringValue = "Hi")
    public static String method3(String s) {
        String res;
        try {
            res = "Hi";
        } catch (NullPointerException npe) {
            // ERROR 1: assignment is not used
            res = "Null";
            throw npe;
        } catch (NumberFormatException nfe) {
            res = "Not a number";
            throw nfe;
        }
        return res;
    }

    @NotNull(type = AnnotationType.VERIFY_ABSENT)
    @Constant
    public static String method4(String s) {
        String res;
        try {
            res = "Hi" + Integer.parseInt(s);
        } catch (NullPointerException npe) {
            res = "Null";
        } catch (NumberFormatException nfe) {
            res = "Not a number";
        } finally {
            res = null;
        }
        return res;
    }

    @NotNull(type = AnnotationType.VERIFY_ABSENT)
    @Constant(type = AnnotationType.VERIFY_ABSENT)
    public static String method5(String s) {
        String res;
        try {
            res = "Hi" + Integer.parseInt(s);
        } catch (NullPointerException | NumberFormatException npe) {
            res = null;
        }
        return res;
    }
}
