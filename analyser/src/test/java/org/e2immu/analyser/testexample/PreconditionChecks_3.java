package org.e2immu.analyser.testexample;

import org.e2immu.annotation.NotNull;

public class PreconditionChecks_3 {
    private Integer integer;

    boolean setInteger$Precondition(int ii) { return this.integer == null && ii >= 0; }
    @NotNull
    public Integer setInteger(int ii) {
        synchronized (this) {
            if (ii < 0) throw new UnsupportedOperationException();
            if (integer != null) throw new UnsupportedOperationException();
            integer = ii;
        }
        return ii >= 0 ? ii : null; // Causes ERROR: evaluates to constant
    }
}
