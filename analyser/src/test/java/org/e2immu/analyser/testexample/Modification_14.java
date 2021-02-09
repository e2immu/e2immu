package org.e2immu.analyser.testexample;

import org.e2immu.annotation.*;

/*
Compare this one to Modification_15.
Example shows that direct assignment into sub-type, even if it causes a warning,
should also count for a modification ?
 */

@E1Immutable // but not a container!
public class Modification_14 {

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
    @Modified
    public final TwoIntegers input;

    public Modification_14(@Modified TwoIntegers input) {
        if (input == null) throw new NullPointerException();
        this.input = input;
    }

    @NotModified
    public int getI() {
        return input.i;
    }

    @Modified
    public void setI(int i) {
        input.setI(i);
    }
}
