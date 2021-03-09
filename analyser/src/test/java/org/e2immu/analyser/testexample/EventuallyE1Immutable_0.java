package org.e2immu.analyser.testexample;

import org.e2immu.annotation.*;

@E1Container(after = "string")
public class EventuallyE1Immutable_0 {

    /* the presence of a field of the TwoIntegers type ensures that EventuallyE1Immutable_0 is not
    level 2 immutable.
     */
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
    @Final(after = "string")
    private String string;

    public EventuallyE1Immutable_0(@NotModified TwoIntegers input) {
        if (input == null) throw new NullPointerException();
        this.input = input;
    }

    public String getString() {
        return string;
    }

    /*
    this order of testing this.string and string currently causes a delay on @NotNull
     */
    @Mark("string")
    public void setString(@NotNull String string) {
        if (this.string != null) throw new UnsupportedOperationException();
        if (string == null) throw new NullPointerException();
        this.string = string;
    }

    /* variant, with the preconditions switched. Result should be the same, but is necessary to test.
     */
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
