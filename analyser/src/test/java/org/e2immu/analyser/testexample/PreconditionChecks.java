package org.e2immu.analyser.testexample;

import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.Precondition;
import org.e2immu.annotation.Variable;

public class PreconditionChecks {

    // the first 4 methods are an exercise in applying a precondition on parameters

    @Precondition("(not (null == e1) or not (null == e2))")
    public static String either(String e1, String e2) {
        if (e1 == null && e2 == null) throw new UnsupportedOperationException();
        return e1 + e2;
    }

    @NotNull
    public static String useEither1(@NotNull String in1) {
        return either(in1, null);
    }

    @NotNull
    public static String useEither2(@NotNull String in2) {
        return either(null, in2);
    }


    // here we want to propagate the precondition from MethodValue down to the method,
    // very much like we propagate the single not-null
    // TODO this has not been implemented yet.
    // an implementation may go via the PropertyWrapper, which can hold the precondition.
    
    @Precondition("not((null == f1) && (null == f2))")
    public static String useEither3(String f1, String f2) {
        return either(f1, f2);
    }


    // check a precondition on a variable field
    // and the combination of a variable field and a parameter

    @Variable
    private int i;

    @Precondition("i >= 0")
    public void setPositive1(int j1) {
        if (i < 0) throw new NullPointerException();
        this.i = j1;
    }

    @Precondition("(i >= 0 and j2 >= 0)")
    public void setPositive2(int j2) {
        if (i < 0) throw new NullPointerException();
        if (j2 < 0) throw new NullPointerException();
        this.i = j2;
    }

    @Precondition("(i >= 0 and j3 >= 0)")
    public void setPositive3(int j3) {
        if (i < 0 || j3 < 0) throw new NullPointerException();
        this.i = j3;
    }

    // this avoid a field not used exception.
    public int getI() {
        return i;
    }
}
