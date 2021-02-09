package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Container;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

/*
Compare this one to Modification_14.
Example shows that direct assignment into sub-type, even if it causes a warning,
should also count for a modification ?

Decision 20200209 for now, we upgrade the warning to an error, and keep @NotModified

 */
public class Modification_15 {

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

    public Modification_15(@NotModified TwoIntegers input) {
        if (input == null) throw new NullPointerException();
        this.input = input;
    }

    @NotModified
    public int getI() {
        return input.i;
    }

    @Modified
    public void setI(int i) {
        input.i = i;
    }
}
