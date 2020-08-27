package org.e2immu.analyser.testexample;

import org.e2immu.annotation.*;

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

    @Precondition("(not (null == f1) or not (null == f2))")
    public static String useEither3(@Nullable String f1, @Nullable String f2) {
        return either(f1, f2);
    }

    // check a precondition on a variable field
    // and the combination of a variable field and a parameter

    @Variable
    private int i;

    @Precondition("this.i >= 0")
    public void setPositive1(int j1) {
        if (i < 0) throw new UnsupportedOperationException();
        this.i = j1;
    }

    @Precondition("j1 >= 0")
    public void setPositive2(int j1) {
        if (j1 < 0) throw new UnsupportedOperationException();
        this.i = j1;
    }

    @Precondition("(this.i >= 0 and j2 >= 0)")
    public void setPositive3(int j2) {
        if (i < 0) throw new UnsupportedOperationException();
        if (j2 < 0) throw new IllegalArgumentException();
        this.i = j2;
    }

    @Precondition("(this.i >= 0 and j3 >= 0)")
    public void setPositive4(int j3) {
        if (i < 0 || j3 < 0) throw new UnsupportedOperationException();
        this.i = j3;
    }

    @Precondition("(this.i >= 0 and j2 >= 0)")
    public void setPositive5(int j2) {
        if (i < 0) throw new UnsupportedOperationException();
        // the analyser should note that i>=0 is redundant
        if (i >= 0 && j2 < 0) throw new IllegalArgumentException();
        this.i = j2;
    }

    // this avoid a field not used exception.
    public int getI() {
        return i;
    }

    // some examples of combined preconditions...
    // this one shows that you cannot simply say in the 2nd case: there was one already!

    @Precondition("(((-2) + p1) >= 0 and p2 > 0)")
    public void combinedPrecondition1(int p1, int p2) {
        if (p1 < 2 || p2 <= 0) throw new UnsupportedOperationException();
        this.i = p1 > p2 ? p1 + 3 : p2;
    }

    @Precondition("(((-2) + p1) >= 0 and p2 > 0)")
    public void combinedPrecondition2(int p1, int p2) {
        if (p1 <= 0) throw new UnsupportedOperationException(); // IRRELEVANT given the next one
        if (p1 < 2 || p2 <= 0) throw new UnsupportedOperationException();
        this.i = p1 > p2 ? p1 + 3 : p2;
    }

    // here, the first condition does not disappear, because of the AND rather than the OR
    @Precondition("(p1 > 0 and (((-2) + p1) >= 0 or p2 > 0))")
    public void combinedPrecondition3(int p1, int p2) {
        if (p1 <= 0) throw new UnsupportedOperationException();
        if (p1 < 2 && p2 <= 0) throw new UnsupportedOperationException();
        this.i = p1 > p2 ? p1 + 3 : p2;
    }

    @Variable
    private Integer integer;

    @NotNull
    @Precondition("(null == this.integer and ii >= 0)")
    public Integer setInteger(int ii) {
        synchronized (this) {
            if (ii < 0) throw new UnsupportedOperationException();
            if (integer != null) throw new UnsupportedOperationException();
            integer = ii;
        }
        return ii >= 0 ? ii : null; // Causes ERROR: evaluates to constant
    }
}
