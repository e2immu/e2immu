package org.e2immu.analyser.testexample;

import org.e2immu.annotation.*;

@E1Container(after = "string")
public class EventuallyE1Immutable1 {

    @Container
    public static class TwoIntegers {
        private int i;
        private int j;

        public int getI() {
            return i;
        }

        public int getJ() {
            return j;
        }

        public void setI(int i) {
            this.i = i;
        }

        public void setJ(int j) {
            this.j = j;
        }
    }

    @NotNull
    @NotModified
    public final TwoIntegers input;
    private String string;

    public EventuallyE1Immutable1(@NotModified TwoIntegers input) {
        if (input == null) throw new NullPointerException();
        this.input = input;
    }

    public String getString() {
        return string;
    }

    @Mark("string")
    public void setString(@NotNull String string) {
        if (this.string != null) throw new UnsupportedOperationException();
        if (string == null) throw new NullPointerException();
        this.string = string;
    }

    // variant, with the preconditions switched
    // result should be the same
    @Mark("string")
    public void setString2(@NotNull String string2) {
        if (string2 == null) throw new NullPointerException();
        if (this.string != null) throw new UnsupportedOperationException();
        this.string = string2;
    }

    @NotModified
    public int getI() {
        return input.i;
    }
}
